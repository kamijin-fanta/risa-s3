package com.github.kamijin_fanta.data

import java.io.File
import java.nio.file.Paths
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ ContentTypes, RequestEntity }
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.FileIO
import akka.stream.{ ActorMaterializer, Materializer }
import com.github.kamijin_fanta.ApplicationConfig
import com.github.kamijin_fanta.common.TerminableService
import com.typesafe.scalalogging.{ LazyLogging, Logger }

import scala.concurrent.{ Await, ExecutionContext, ExecutionContextExecutor, Future }
import scala.concurrent.duration._

case class RisaHttpDataService(implicit applicationConfig: ApplicationConfig, system: ActorSystem)
  extends LazyLogging with TerminableService {
  private var bind: ServerBinding = _

  override def run()(implicit ctx: ExecutionContextExecutor): Future[Unit] = {
    implicit val mat: ActorMaterializer = ActorMaterializer()
    implicit val ctx: ExecutionContext = system.dispatcher
    println("#######################")

    import slick.jdbc.MySQLProfile.api._
    val db = slick.jdbc.MySQLProfile.api.Database.forConfig("database")
    try {
      val stm = Tables.AccessKey.result
      println(s"SQL: $stm")
      val res = db.run(stm)
      val rows = Await.result(res, 1 seconds).toList
      println(s"rows: $rows")
    } finally {
      db.close()
    }

    Http()
      .bindAndHandle(rootRoute(logger), "0.0.0.0", applicationConfig.data.port)
      .map { b =>
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
      Future {}
    }
  }

  def rootRoute(logger: Logger)(implicit mat: Materializer, ctx: ExecutionContext, conf: ApplicationConfig): Route = {
    import akka.http.scaladsl.server.Directives._
    //    import akka.http.scaladsl.server.Directives.{Segment, complete, extractRequestEntity, get, getFromFile, onSuccess, path, post}
    //    import akka.http.scaladsl.server.RouteConcatenation._

    def newFile(entity: RequestEntity): Route = {
      logger.debug(s"Put new object ${entity.contentLengthOption.getOrElse("---")}")

      val dir = "0001"
      val filename = UUID.randomUUID().toString
      val to = FileIO.toPath(Paths.get(applicationConfig.data.baseDir, dir, filename))
      val res = entity.dataBytes.runWith(to)
      val path = s"/$dir/$filename"
      onSuccess(res) { res =>
        complete(path)
      }
    }
    def getFile(dir: String, name: String): Route = {
      val base = applicationConfig.data.baseDir
      val path = s"$base/$dir/$name".replace("..", "")
      val file = new File(path)
      logger.debug(s"get access $path ${file.isFile} ${file.canRead}")
      getFromFile(file, ContentTypes.NoContentType)
    }

    (post & path("new") & extractRequestEntity) { entity =>
      newFile(entity)
    } ~ (get & path(Segment / Segment)) { (dir, name) =>
      getFile(dir, name)
    }
  }
}
