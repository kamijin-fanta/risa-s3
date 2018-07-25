package com.github.kamijin_fanta.data

import Tables._
import akka.actor.{FSM, Props}
import com.github.kamijin_fanta.common.ActorSystemServiceComponent
import com.github.kamijin_fanta.data.ClusterManagement.ReceiveClusterInfo

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

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
    volumeGroupRow: VolumeGroupRow,
    ) extends Data

  sealed trait Events
  case class ReceiveClusterInfo(volumeGroupRow: VolumeGroupRow) extends Events
  case object Fetch extends Events
  case class Rejection(throwable: Throwable) extends  Events
  case object BootstrapComplete extends Events
}

trait ClusterManagementServiceComponent extends ActorSystemServiceComponent {

  def ClusterManagementService = new ClusterManagementService

  class ClusterManagementService {
    val stateMachine = actorSystem.actorOf(Props(new ClusterManagementStateMachine(this)))

    def fetchClusterInfo(): Future[ReceiveClusterInfo] = {
      ???
    }

    def startBootstrap(): Unit = {
      ???
    }
    def waitCompaction(): Future[Unit] = {
      ???
    }
  }

  class ClusterManagementStateMachine(client: ClusterManagementService) extends FSM[ClusterManagement.State, ClusterManagement.Data] {
    import ClusterManagement._

    implicit val ctx = context.dispatcher

    startWith(Idle, Uninitialized)

    when(Idle, 1 seconds) {
      case Event(StateTimeout, _) =>
        goto(Initialize)
    }

    when(Initialize, 10 seconds) {
      case Event(StateTimeout, _) =>
        goto(Idle)
      case Event(e: ReceiveClusterInfo, _) =>
        goto(Bootstrap) using ClusterSettings(e.volumeGroupRow)
      case Event(e: Rejection, _) =>
        goto(Idle)
    }
    onTransition {
      case _ -> Initialize =>
        client.fetchClusterInfo().onComplete{
          case Success(info) => self ! info
          case Failure(th) =>
            log.error(th, "cluster info fetch error")
            self ! Rejection(th)
        }
    }

    when(Bootstrap) {
      case Event(BootstrapComplete , cluster: ClusterSettings) =>
        goto(Started)
    }
    onTransition {
      case _ -> Bootstrap =>
        client.startBootstrap()
        client.waitCompaction().onComplete {
          case Success(_) => self ! BootstrapComplete
        }
    }

    initialize()
  }

}
