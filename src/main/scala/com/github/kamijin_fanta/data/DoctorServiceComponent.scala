package com.github.kamijin_fanta.data

import akka.actor.{ FSM, Props }
import akka.util.Timeout
import com.github.kamijin_fanta.common.ActorSystemServiceComponent
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future, Promise }
import scala.util.{ Failure, Success }

object DoctorStateMachineConst {
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
  final case class DoctorInfo(
    repairType: RepairType,
    currentTablet: Option[String],
    reamingFiles: Seq[String]) extends Data

  sealed trait Events
  case object Start extends Events
  case class FetchNextTablet(current: Option[String]) extends Events
  case class ReceiveTablet(tablet: Option[String], reamingFiles: Seq[String]) extends Events
  case class FilterFiles(repairType: RepairType, tablet: String, files: Seq[String]) extends Events
  case class ReceiveFilterFiles(tablet: String, files: Seq[String]) extends Events
  case class DownloadTabletItems(tablet: String, files: Seq[String]) extends Events
  case class Rejection(throwable: Throwable) extends Events
  case object DownloadTabletItemsComplete extends Events
  case object AskCurrentStats extends Events
  case class CurrentStats(state: State, data: DoctorInfo) extends Events
}

trait DoctorServiceComponent extends ActorSystemServiceComponent {
  import DoctorStateMachineConst._

  def doctorService = new DoctorService

  class DoctorService extends LazyLogging {
    val doctorStateMachine = actorSystem.actorOf(Props(new DoctorStateMachine(this)))

    def fetchNextTablet(currentTablet: Option[String]): Future[ReceiveTablet] = {
      ???
    }
    def filterFiles(repairType: RepairType, tablet: String, files: Seq[String]): Future[ReceiveFilterFiles] = {
      ???
    }
    def downloadTabletItems(tablet: String, files: Seq[String]): Future[Unit] = {
      ???
    }
    def startRepair(): Unit = {
      doctorStateMachine ! Start
    }
    def waitComplete()(implicit ctx: ExecutionContextExecutor): Future[Unit] = {
      val promise = Promise[Unit]
      val c = actorSystem.scheduler.schedule(0 second, 1 second) {
        import akka.pattern.ask
        implicit val timeout = Timeout(500 millis)
        (doctorStateMachine ? AskCurrentStats).mapTo[CurrentStats].onComplete {
          case Success(v) =>
            if (v.state == Complete) promise.success()
          case Failure(th) =>
            logger.error("error fetch current stats", th)
            promise.failure(th)
        }
      }

      promise.future
    }
  }

  class DoctorStateMachine(client: DoctorService) extends FSM[DoctorStateMachineConst.State, DoctorStateMachineConst.Data] {
    implicit val ctx = context.dispatcher

    startWith(Idle, DoctorInfo(FullRepair, None, Seq()))

    when(Idle) {
      case Event(Start, info: DoctorInfo) =>
        goto(SearchNextTablet)
    }

    when(SearchNextTablet) {
      case Event(e: ReceiveTablet, info: DoctorInfo) =>
        val nextData = info.copy(
          currentTablet = e.tablet,
          reamingFiles = e.reamingFiles)
        (e.tablet, info.repairType) match {
          case (None, _) => goto(Complete) using nextData
          case (_, QuickRepair) => goto(QuickTabletFilter) using nextData
          case (_, FullRepair) => goto(FullTabletFilter) using nextData
        }
      case Event(Rejection(th), _) =>
        log.error(th, "error on SearchNextTablet")
        goto(Complete)
    }
    onTransition {
      case _ -> SearchNextTablet =>
        stateData match {
          case info: DoctorInfo =>
            client.fetchNextTablet(info.currentTablet).onComplete {
              case Success(e) => self ! e
              case Failure(th) => self ! Rejection(th)
            }
          case _ =>
        }
    }

    when(QuickTabletFilter) {
      case Event(e: ReceiveFilterFiles, info: DoctorInfo) =>
        goto(FullTabletFilter) using info.copy(reamingFiles = e.files)
      case Event(Rejection(th), _) =>
        log.error(th, "error on QuickTabletFilter")
        goto(SearchNextTablet)
    }
    onTransition {
      case _ -> QuickTabletFilter =>
        stateData match {
          case DoctorInfo(_, Some(tablet), files) =>
            client.filterFiles(QuickRepair, tablet, files).onComplete {
              case Success(e) => self ! e
              case Failure(th) => self ! Rejection(th)
            }
          case _ =>
        }
    }

    when(FullTabletFilter) {
      case Event(e: ReceiveFilterFiles, info: DoctorInfo) =>
        goto(DownloadTabletItems) using info.copy(reamingFiles = e.files)
      case Event(Rejection(th), _) =>
        log.error(th, "error on FullTabletFilter")
        goto(SearchNextTablet)
    }
    onTransition {
      case _ -> FullTabletFilter =>
        stateData match {
          case DoctorInfo(_, Some(tablet), files) =>
            client.filterFiles(FullRepair, tablet, files).onComplete {
              case Success(e) => self ! e
              case Failure(th) => self ! Rejection(th)
            }
          case _ =>
        }
    }

    when(DownloadTabletItems) {
      case Event(DownloadTabletItemsComplete, info: DoctorInfo) =>
        goto(SearchNextTablet)
      case Event(Rejection(th), _) =>
        log.error(th, "error on DownloadTabletItems")
        goto(SearchNextTablet)
    }
    onTransition {
      case _ -> DownloadTabletItems =>
        stateData match {
          case DoctorInfo(_, Some(tablet), files) =>
            client.downloadTabletItems(tablet, files).onComplete {
              case Success(e) => self ! e
              case Failure(th) => self ! Rejection(th)
            }
          case _ =>
        }
    }

    onTransition {
      case before -> after =>
        log.debug(s"change state $before -> $after")
    }
    initialize()
  }
}
