package com.github.kamijin_fanta.data

import Tables._
import akka.actor.{ FSM, Props }
import com.github.kamijin_fanta.common.{ ActorSystemServiceComponent, ApplicationConfigComponent, DbServiceComponent }
import com.github.kamijin_fanta.data.ClusterManagement._

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success }

object ClusterManagement {
  sealed trait State
  case object Idle extends State
  case object Initialize extends State
  case object Bootstrap extends State
  case object Started extends State
  case object Leave extends State

  sealed trait Data
  case object Uninitialized extends Data
  final case class ClusterSettings(
    volumeGroupRow: VolumeGroupRow) extends Data

  sealed trait Events
  case class ReceiveClusterInfo(volumeGroupRow: VolumeGroupRow) extends Events
  case object Fetch extends Events
  case class Rejection(throwable: Throwable) extends Events
  case object BootstrapComplete extends Events
  case object RequestCurrentClusterSettings extends Events
  case class CurrentClusterSettings(clusterSettings: Data) extends Events
}

trait ClusterManagementServiceComponent {
  self: ActorSystemServiceComponent with ApplicationConfigComponent with DbServiceComponent with DoctorServiceComponent =>

  def clusterManagementService: ClusterManagementService

  class ClusterManagementService {
    import slick.jdbc.MySQLProfile.api._
    var currentClusterData: Data = Uninitialized

    def init() = {
      val stateMachine = actorSystem.actorOf(Props(new ClusterManagementStateMachine(this)))
      doctorService.init()
    }

    def fetchClusterInfo()(implicit ctx: ExecutionContextExecutor): Future[ReceiveClusterInfo] = {
      val volumeGroup = applicationConfig.data.group
      dbService.backend.run(
        VolumeGroup.filter(_.id === volumeGroup).result.headOption)
        .map(_.getOrElse(throw new Exception(s"Not Found VolumeGroup #$volumeGroup")))
        .map(v => ReceiveClusterInfo(v))
    }

    def startBootstrap(): Unit = {
      doctorService.startRepair()
    }
    def waitRepair()(implicit ctx: ExecutionContextExecutor): Future[Unit] = {
      doctorService.waitComplete()
    }

    def currentClusterSetting(): Option[ClusterSettings] = {
      currentClusterData match {
        case c: ClusterSettings => Some(c)
        case _ => None
      }
    }

    def _updateClusterInfo(_currentClusterSettings: CurrentClusterSettings): Unit = {
      currentClusterData = _currentClusterSettings.clusterSettings
    }
  }

  class ClusterManagementStateMachine(client: ClusterManagementService) extends FSM[ClusterManagement.State, ClusterManagement.Data] {
    import ClusterManagement._

    implicit val ctx = context.dispatcher

    startWith(Idle, Uninitialized)

    log.debug("#########")

    when(Idle, 1 seconds) {
      case Event(StateTimeout, _) =>
        goto(Initialize)
    }

    when(Initialize, 30 seconds) {
      case Event(StateTimeout, _) =>
        goto(Idle)
      case Event(e: ReceiveClusterInfo, _) =>
        log.debug(s"receive cluster info $e")
        goto(Bootstrap) using ClusterSettings(e.volumeGroupRow)
      case Event(e: Rejection, _) =>
        goto(Idle)
    }
    onTransition {
      case _ -> Initialize =>
        log.debug("start initialize")
        client.fetchClusterInfo().onComplete {
          case Success(info) => self ! info
          case Failure(th) =>
            log.error(th, "cluster info fetch error")
            self ! Rejection(th)
        }
    }

    when(Bootstrap) {
      case Event(BootstrapComplete, cluster: ClusterSettings) =>
        goto(Started)
      case Event(e: Rejection, _) =>
        goto(Idle)
    }
    onTransition {
      case _ -> Bootstrap =>
        client.startBootstrap()
        client.waitRepair().onComplete {
          case Success(_) => self ! BootstrapComplete
          case Failure(th) =>
            log.error(th, "bootstrap failure")
            self ! Rejection(th)
        }
    }

    onTransition {
      case before -> after =>
        log.debug(s"change state $before -> $after")
        client._updateClusterInfo(CurrentClusterSettings(stateData))
    }

    initialize()
  }

}
