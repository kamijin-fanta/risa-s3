package com.github.kamijin_fanta.data

import Tables._
import akka.actor.{ FSM, Props }
import com.github.kamijin_fanta.common.{ ActorSystemServiceComponent, ApplicationConfigComponent, DbServiceComponent }
import com.github.kamijin_fanta.data.ClusterManagement._
import com.github.kamijin_fanta.data.metaProvider.MetaBackendServiceComponent

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success }

object ClusterManagement {
  sealed trait State
  case object Idle extends State
  case object Initialize extends State
  case object Bootstrap extends State
  case object Joining extends State
  case object Started extends State
  case object Leave extends State

  sealed trait Data
  case object Uninitialized extends Data
  final case class ClusterSettings(
    nodeId: Int,
    volumeGroupRow: VolumeGroupRow,
    volumeNodeRow: VolumeNodeRow,
    otherNodes: Seq[VolumeNodeRow]) extends Data

  sealed trait Events
  case class ReceiveClusterInfo(clusterSettings: ClusterSettings) extends Events
  case object Fetch extends Events
  case class Rejection(throwable: Throwable) extends Events
  case object BootstrapComplete extends Events
  case object RequestCurrentClusterSettings extends Events
  case object Tick extends Events
}

trait ClusterManagementServiceComponent {
  self: ActorSystemServiceComponent with ApplicationConfigComponent with DbServiceComponent with DoctorServiceComponent with MetaBackendServiceComponent with StorageServiceComponent =>

  def clusterManagementService: ClusterManagementService

  class ClusterManagementService {
    @volatile
    private var currentClusterData: Data = Uninitialized

    def init() = {
      val stateMachine = actorSystem.actorOf(Props(new ClusterManagementStateMachine(this)))
      doctorService.init()
    }

    def getOrCreateNodeId()(implicit ctx: ExecutionContextExecutor): Future[Int] = {
      currentClusterSetting() match {
        case Some(setting) =>
          Future.successful(setting.nodeId)
        case None =>
          storageService.readNodeId().flatMap {
            case Some(id) =>
              Future.successful(id)
            case None =>
              for {
                nodeId <- metaBackendService.newNode(
                  VolumeNodeRow(
                    0,
                    applicationConfig.data.group,
                    s"http://${applicationConfig.data.exposeHost}:${applicationConfig.data.port}",
                    0L,
                    0L))
                _ <- storageService.writeNodeId(nodeId)
              } yield nodeId
          }
      }
    }

    def fetchClusterInfo()(implicit ctx: ExecutionContextExecutor): Future[ReceiveClusterInfo] = {
      val volumeGroup = applicationConfig.data.group

      // todo read file
      for {
        nodeId <- getOrCreateNodeId()
        group <- metaBackendService.selfGroup(volumeGroup)
          .map(_.getOrElse(throw new Exception(s"Not Found VolumeGroup #$volumeGroup")))
        node <- metaBackendService.node(volumeGroup, nodeId)
          .map(_.getOrElse(throw new Exception(s"Not Found VolumeNode #$volumeGroup")))
        other <- metaBackendService.otherNodes()(applicationConfig)
      } yield ReceiveClusterInfo(ClusterSettings(nodeId, group, node, other))
    }

    def updateClusterInfo(currentCluster: ClusterSettings)(implicit ctx: ExecutionContextExecutor): Future[ReceiveClusterInfo] = {
      for {
        udpate <- metaBackendService.updateNode(
          VolumeNodeRow(
            currentCluster.nodeId,
            applicationConfig.data.group,
            s"http://${applicationConfig.data.exposeHost}:${applicationConfig.data.port}",
            100000000L,
            100000000L // todo
          ))
        info <- fetchClusterInfo()
      } yield info
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

    def _updateClusterInfo(data: Data): Unit = {
      synchronized {
        currentClusterData = data
      }
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
        client._updateClusterInfo(e.clusterSettings)
        goto(Bootstrap) using e.clusterSettings
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

    when(Started) {
      case Event(Tick, cluster: ClusterSettings) =>
        client.updateClusterInfo(cluster).onComplete {
          case Success(info) =>
            client._updateClusterInfo(info.clusterSettings)
          case Failure(th) =>
            log.error(th, "cluster info fetch error")
        }
        stay
    }
    val tickTimerKey = "tickTimerKey"
    onTransition {
      case _ -> Started =>
        setTimer(tickTimerKey, Tick, 15 seconds, true)
      case Started -> _ =>
        cancelTimer(tickTimerKey)
    }

    onTransition {
      case before -> after =>
        log.debug(s"change state $before -> $after")
    }

    initialize()
  }

}
