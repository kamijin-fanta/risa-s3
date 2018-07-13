package com.github.kamijin_fanta.data

import java.io.File
import java.nio.file.Paths
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ ContentTypes, RequestEntity, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{ FileIO, Sink }
import akka.stream.{ ActorMaterializer, IOResult, Materializer }
import akka.util.ByteString
import com.github.kamijin_fanta.ApplicationConfig
import com.github.kamijin_fanta.common.{ DbServiceComponent, TerminableService }
import com.github.kamijin_fanta.data.metaProvider.{ LocalMetaBackendService, MetaBackendServiceComponent }
import com.typesafe.scalalogging.{ LazyLogging, Logger }

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }

case class RisaHttpDataService(implicit applicationConfig: ApplicationConfig, system: ActorSystem)
  extends LazyLogging with TerminableService with DbServiceComponent with MetaBackendServiceComponent {
  private var bind: ServerBinding = _
  var dbService: DbService = _

  override def metaBackendService: LocalMetaBackendService = new LocalMetaBackendService()(system.dispatcher)

  override def run()(implicit ctx: ExecutionContextExecutor): Future[Unit] = {
    implicit val mat: ActorMaterializer = ActorMaterializer()
    implicit val ctx: ExecutionContext = system.dispatcher

    val db = slick.jdbc.MySQLProfile.api.Database.forConfig("database")
    dbService = new DbService(db)

    Http()
      .bindAndHandle(rootRoute(logger), "0.0.0.0", applicationConfig.data.port)
      .map { b =>
        logger.info("listen: " + b.localAddress.toString)
        this.synchronized(bind = b)
        ()
      }
  }

  override def terminate()(implicit ctx: ExecutionContextExecutor): Future[Unit] = {
    if (bind != null) {
      bind.unbind().map { _ =>
        synchronized {
          bind = null
        }
      }
    } else {
      Future.successful()
    } map { _ =>
      if (dbService != null) dbService.terminate()
    }
  }

  def rootRoute(logger: Logger)(implicit mat: Materializer, ctx: ExecutionContext, conf: ApplicationConfig): Route = {
    import akka.http.scaladsl.server.Directives._
    //    import akka.http.scaladsl.server.Directives.{Segment, complete, extractRequestEntity, get, getFromFile, onSuccess, path, post}
    //    import akka.http.scaladsl.server.RouteConcatenation._

    case class TabletItem(tablet: String, itemName: String)
    def tabletItemAllocator(capacity: Long): Future[TabletItem] = {
      val randomName = UUID.randomUUID().toString
      Future.successful(TabletItem("0001", randomName))
    }

    def newFile(entity: RequestEntity): Route = {
      entity.contentLengthOption match {
        case Some(length) =>
          logger.debug(s"Put new object ContentLength:${length}")
          val iores = for {
            tabletItem <- tabletItemAllocator(length)
            to = FileIO.toPath(toFile(tabletItem.tablet, tabletItem.itemName).toPath)
            res <- entity.dataBytes.runWith(to)
          } yield (tabletItem, res)

          onSuccess(iores) { (tablet, res) =>
            // todo commit to database
            val path = s"/${tablet.tablet}/${tablet.itemName}"
            complete(path)
          }
        case None => failWith(new AssertionError("need header content-length"))
      }
    }
    def internalNewFile(entity: RequestEntity, tablet: String, name: String): Route = {
      val localSink = FileIO.toPath(toFile(tablet, name).toPath)
      val result = entity.dataBytes.runWith(localSink)
      onSuccess(result) { res =>
        if (res.status.isSuccess) complete("ok")
        else failWith(res.getError)
      }
    }

    def toFile(tablet: String, name: String) = {
      val base = applicationConfig.data.baseDir
      val path = s"$base/$tablet/$name".replace("..", "")
      new File(path)
    }
    def getFile(tablet: String, name: String): Route = {
      val file = toFile(tablet, name)
      logger.debug(s"get access ${file.toPath} ${file.isFile} ${file.canRead}")
      getFromFile(file, ContentTypes.NoContentType)
    }
    def deleteFile(tablet: String, name: String): Route = {
      // todo commit to database
      val file = toFile(tablet, name)
      logger.debug(s"delete ${file.toPath} ${file.isFile} ${file.canRead}")

      if (file.delete()) complete("ok")
      else complete(StatusCodes.NotFound, "content not found")
    }
    def internalDeleteFile(tablet: String, name: String): Route = {
      val file = toFile(tablet, name)
      logger.debug(s"delete ${file.toPath} ${file.isFile} ${file.canRead}")

      if (file.delete()) complete("ok")
      else complete(StatusCodes.NotFound, "content not found")
    }

    pathPrefix("object") {
      (post & extractRequestEntity) { entity =>
        newFile(entity)
      } ~ path(Segment / Segment) { (tablet, name) =>
        get {
          getFile(tablet, name)
        } ~ delete {
          deleteFile(tablet, name)
        }
      }
    } ~ pathPrefix("internal") {
      path("object" / Segment / Segment) { (tablet, name) =>
        (post & extractRequestEntity) { entity =>
          internalNewFile(entity, tablet, name)
        } ~ get {
          getFile(tablet, name)
        } ~ delete {
          internalDeleteFile(tablet, name)
        }
      } ~ get {
        val otherNode = metaBackendService.otherNodes(applicationConfig.data.group, applicationConfig.data.node)
        onSuccess(otherNode) { res =>
          complete(s"other: $res")
        }
      }
    }
  }
}
