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

  sealed trait Events
  case class ReceiveClusterInfo(volumeGroupRow: VolumeGroupRow) extends Events
  case object Fetch extends Events
}

object ClusterCompaction {
  sealed trait State
  case object Idle extends State
  case object SearchNextTablet extends State
  case object QuickTabletFilter extends State
  case object FullTabletFilter extends State
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

  sealed trait Events
  case object Start extends Events
  case class FetchNextTablet(current: Option[String])
  case class ReceiveTablet(tablet: Option[String], reamingFiles: Seq[String]) extends Events
  case class FilterFiles(repairType: RepairType, tablet: String, files: Seq[String])
  case class ReceiveFilterFiles(tablet: String, files: Seq[String])
  case class FetchTabletItems(tablet: String, files: Seq[String])
  case object ReceiveFetchTabletItems
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

    when(Idle) {
      case Event(Start, info: CompactionInfo) =>
        goto(SearchNextTablet)
    }

    when(SearchNextTablet) {
      case Event(e: ReceiveTablet, info: CompactionInfo) =>
        val nextData = info.copy(
          currentTablet = e.tablet,
          reamingFiles = e.reamingFiles
        )
        (e.tablet, info.repairType) match {
          case (None, _) => goto(Complete) using nextData
          case (_, QuickRepair) => goto(QuickTabletFilter) using nextData
          case (_, FullRepair) => goto(FullTabletFilter) using nextData
        }
    }

    onTransition {
      case _ -> SearchNextTablet =>
        stateData match {
          case info :CompactionInfo =>
            client ! FetchNextTablet(info.currentTablet)
          case _ =>
        }
    }

    when(QuickTabletFilter) {
      case Event(e: ReceiveFilterFiles, info: CompactionInfo) =>
        goto(FullTabletFilter) using info.copy(reamingFiles = e.files)
    }
    onTransition {
      case _ -> QuickTabletFilter =>
        stateData match {
          case CompactionInfo(_, Some(tablet), files) =>
            client ! FilterFiles(QuickRepair, tablet, files)
          case _ =>
        }
    }

    when(FullTabletFilter) {
      case Event(e: ReceiveFilterFiles, info: CompactionInfo) =>
        goto(DownloadTabletItems) using info.copy(reamingFiles = e.files)
    }
    onTransition {
      case _ -> FullTabletFilter =>
        stateData match {
          case CompactionInfo(_, Some(tablet), files) =>
            client ! FilterFiles(FullRepair, tablet, files)
          case _ =>
        }
    }

    when(DownloadTabletItems) {
      case Event(ReceiveFetchTabletItems, info: CompactionInfo) =>
        goto(SearchNextTablet)
    }
    onTransition {
      case _ -> DownloadTabletItems =>
        stateData match {
          case CompactionInfo(_, Some(tablet), files) =>
            client ! FetchTabletItems(tablet, files)
          case _ =>
        }
    }
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
