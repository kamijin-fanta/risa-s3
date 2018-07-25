package com.github.kamijin_fanta.data

import java.io.File
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
import com.typesafe.scalalogging.{ LazyLogging, Logger }

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }
import scala.concurrent.duration._

case class RisaHttpDataService(_system: ActorSystem, _applicationConfig: ApplicationConfig)
  extends LazyLogging
  with TerminableService
  with DbServiceComponent
  with MetaBackendServiceComponent
  with ActorSystemServiceComponent
  with ApplicationConfigComponent
  with ClusterManagementServiceComponent
  with DoctorServiceComponent {

  private var bind: ServerBinding = _
  var dbService: DbService = _

  override def doctorService: DoctorService = new DoctorService
  override def clusterManagementService: ClusterManagementService = new ClusterManagementService

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

    case class TabletItem(tablet: String, itemName: String)
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
            localPath = toFile(tabletItem.tablet, tabletItem.itemName).toPath
            to = FileIO.toPath(localPath)
            ioRes <- entity.dataBytes.runWith(to)
            otherNodeRes <- otherNodesRequests(otherNodes, tabletItem, localPath)
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
      val localSink = FileIO.toPath(toFile(tablet, name).toPath)
      val source = entity.dataBytes

      val result = source.runWith(localSink)
      onSuccess(result) { res =>
        logger.info(s"internal upload complete $res")
        if (res.wasSuccessful) complete(res.count.toString)
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
      // database commit -> delete local file -> delete remote file

      logger.debug(s"delete $tablet/$name")

      def deleteLocalFile(): Future[Unit] = {
        val file = toFile(tablet, name)
        file.delete()
        Future.successful()
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
