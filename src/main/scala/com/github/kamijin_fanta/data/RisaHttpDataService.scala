package com.github.kamijin_fanta.data

import java.nio.file.NoSuchFileException
import java.util.UUID

import Tables._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.stream._
import akka.stream.scaladsl.FileIO
import com.github.kamijin_fanta.ApplicationConfig
import com.github.kamijin_fanta.common._
import com.github.kamijin_fanta.common.model.Implement._
import com.github.kamijin_fanta.data.metaProvider.MetaBackendServiceComponent
import com.github.kamijin_fanta.data.model.TabletItem
import com.typesafe.scalalogging.{ LazyLogging, Logger }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }

case class RisaHttpDataService(_system: ActorSystem, _applicationConfig: ApplicationConfig)
  extends LazyLogging
  with TerminableService
  with DbServiceComponent
  with MetaBackendServiceComponent
  with ActorSystemServiceComponent
  with ApplicationConfigComponent
  with ClusterManagementServiceComponent
  with DoctorServiceComponent
  with StorageServiceComponent {

  private var bind: ServerBinding = _
  var dbService: DbService = _

  override lazy val doctorService: DoctorService = new DoctorService
  override lazy val clusterManagementService: ClusterManagementService = new ClusterManagementService
  override lazy val storageService: StorageService = new StorageService

  override implicit val actorSystem: ActorSystem = _system
  override implicit val applicationConfig: ApplicationConfig = _applicationConfig

  override def metaBackendService: MetaBackendService = new MetaBackendService(dbService)(actorSystem.dispatcher)

  override def run()(implicit ctx: ExecutionContextExecutor): Future[Unit] = {
    implicit val mat: ActorMaterializer = ActorMaterializer()
    implicit val ctx: ExecutionContext = actorSystem.dispatcher

    val db = slick.jdbc.MySQLProfile.api.Database.forConfig("database")
    dbService = DbService(db)
    clusterManagementService.init()

    Http()
      .bindAndHandle(rootRoute(logger), "0.0.0.0", applicationConfig.data.port)
      .map { b =>
        logger.info("listen: " + b.localAddress.toString)
        this.synchronized {
          bind = b
        }
        ()
      }
  }

  override def terminate()(implicit ctx: ExecutionContextExecutor): Future[Unit] = {
    if (bind != null) {
      logger.info("Graceful termination...")
      bind.terminate(hardDeadline = 10 second)
        .map { _ =>
          this.synchronized {
            bind = null
          }
        }
    } else {
      Future.successful()
    } map { _ =>
      if (dbService != null) dbService.terminate()
    }
  }

  def rootRoute(logger: Logger)(implicit mat: Materializer, ctx: ExecutionContext): Route = {
    import akka.http.scaladsl.server.Directives._
    //    import akka.http.scaladsl.server.Directives.{Segment, complete, extractRequestEntity, get, getFromFile, onSuccess, path, post}
    //    import akka.http.scaladsl.server.RouteConcatenation._

    def tabletItemAllocator(capacity: Long): Future[TabletItem] = {
      val randomName = UUID.randomUUID().toString
      Future.successful(TabletItem("0001", randomName))
    }

    def otherNodesRequests(otherNodes: Seq[VolumeNodeRow], tabletItem: TabletItem, inputPath: java.nio.file.Path) = {
      val requestEntity = HttpEntity(
        ContentTypes.NoContentType,
        FileIO.fromPath(inputPath))
      val path = Path(s"/internal/object/${tabletItem.tablet}/${tabletItem.itemName}")
      val res = otherNodes
        .map(node => HttpRequest(
          HttpMethods.POST,
          node.apiBaseUri.withPath(path),
          entity = requestEntity))
        .map(req => Http().singleRequest(req))
      Future.sequence(res)
    }

    def newFile(entity: RequestEntity): Route = {
      entity.contentLengthOption match {
        case Some(length) =>
          logger.debug(s"Put new object ContentLength:${length}")

          // todo check checksum / timeout / delete incomplete files
          val iores = for {
            tabletItem <- tabletItemAllocator(length)
            otherNodes <- metaBackendService.otherNodes()
            to = storageService.writeFile(tabletItem)
            ioRes <- entity.dataBytes.runWith(to)
            otherNodeRes <- otherNodesRequests(otherNodes, tabletItem, storageService.toPath(tabletItem))
          } yield (tabletItem, ioRes, otherNodeRes)

          onSuccess(iores) { (tablet, ioRes, otherNodes) =>
            // todo commit to database
            // todo http error handling
            logger.info(s"complete ${tablet} ${ioRes} ${otherNodes}")
            val path = s"/${tablet.tablet}/${tablet.itemName}"
            if (otherNodes.forall(_.status.isSuccess())) complete(path)
            else complete(StatusCodes.InternalServerError, "internal server error")
          }
        case None => failWith(new AssertionError("need header content-length"))
      }
    }

    def internalNewFile(entity: RequestEntity, tablet: String, name: String): Route = {
      val localSink = storageService.writeFile(TabletItem(tablet, name))
      val source = entity.dataBytes

      val result = source.runWith(localSink)
      onSuccess(result) { res =>
        logger.info(s"internal upload complete $res")
        if (res.wasSuccessful) complete(res.count.toString)
        else failWith(res.getError)
      }
    }

    def getFile(tablet: String, name: String): Route = {
      val file = storageService.toPath(TabletItem(tablet, name))
      getFromFile(file.toFile, ContentTypes.NoContentType)
    }

    def deleteFile(tablet: String, name: String): Route = {
      // todo commit to database
      // database commit -> delete local file -> delete remote file

      logger.debug(s"delete $tablet/$name")

      def deleteLocalFile(): Future[Unit] = {
        Future {
          storageService.deleteFile(TabletItem(tablet, name))
        }
      }
      def deleteRemoteFiles(): Future[Seq[HttpResponse]] = {
        val requests = for {
          nodes <- metaBackendService.otherNodes()
          reqs = nodes.map { node =>
            HttpRequest(
              HttpMethods.DELETE,
              node.apiBaseUri.withPath(Path(s"/internal/object/$tablet/$name")))
          }
        } yield {
          Future.sequence(reqs.map(r => Http().singleRequest(r)))
        }
        requests.flatten
      }

      val remoteResponse = for {
        _ <- deleteLocalFile()
        res <- deleteRemoteFiles()
      } yield res

      onSuccess(remoteResponse) { res =>
        if (res.forall(_.status.isSuccess())) complete("OK")
        else complete(StatusCodes.ServiceUnavailable, "Service Unavailable")
      }
    }

    def internalDeleteFile(tablet: String, name: String): Route = {
      val tabletItem = TabletItem(tablet, name)
      try {
        storageService.deleteFile(tabletItem)
        complete("ok")
      } catch {
        case _: NoSuchFileException =>
          complete(StatusCodes.NotFound, "content not found")
        case th: Throwable =>
          logger.error("internal delete file error", th)
          complete(StatusCodes.InternalServerError, "internal server error")
      }
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
      }
    }
  }
}
