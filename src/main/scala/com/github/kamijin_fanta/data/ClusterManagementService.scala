package com.github.kamijin_fanta.data

import akka.actor.{ActorRef, FSM}
import Tables._

import scala.concurrent.duration._

object ClusterInfo {
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

  sealed trait Event
  case class ReceiveClusterInfo(volumeGroupRow: VolumeGroupRow)
  case object Fetch
}

object ClusterCompaction {
  sealed trait State
  case object Idle extends State
  case object SearchNextTablet extends State
  case object QuickTabletFilter extends State
  case object FullTabletFilter extends State
  case object HashChecking extends State
  case object DownloadTabletItems extends State
  case object Complete extends State

  sealed trait RepairType
  case object QuickRepair extends RepairType
  case object FullRepair extends RepairType

  sealed trait Data
  final case class CompactionInfo(
                                   repairType: RepairType,
                                   currentTablet: Option[String],
                                   reamingFiles: Seq[String]
                                 ) extends Data

  sealed trait Event
  case object Start
}

trait ClusterManagementService {

  class ClusterManagementActor(client: ActorRef) extends FSM[ClusterInfo.State, ClusterInfo.Data] {

    import ClusterInfo._
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
    }
    onTransition {
      case _ -> Initialize =>
        client ! Fetch
    }

    when(Bootstrap) {
      case Event(_, cluster: ClusterSettings) => ???
    }

    initialize()
  }

  class ClusterCompactionManager(client: ActorRef) extends FSM[ClusterCompaction.State, ClusterCompaction.Data] {
    import ClusterCompaction._

    startWith(Idle, CompactionInfo(FullRepair, None, Seq()))
  }
}

/*

Cluster Info
- Initialize
- Bootstrap
- Started
- Leave

Data Management(type: quick|full, currentTablet, reamingFiles)
- Not Work
- Switch next tablet
- Quick tablet repair (only checking name/capacity)
- Hash checking
- Tablet item downloading
- Complete

*/
