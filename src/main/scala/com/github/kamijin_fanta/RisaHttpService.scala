package com.github.kamijin_fanta

import java.nio.file.{ Files, Paths }
import java.time.OffsetDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.FileIO
import akka.stream.{ ActorMaterializer, Materializer }
import com.github.kamijin_fanta.aws4.{ AccessCredential, AccountProvider, AwsSig4StreamStage }
import com.github.kamijin_fanta.response.{ Bucket, Content, ListAllMyBucketsResult, ListBucketResult }
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }

case class RisaHttpService(port: Int)(implicit system: ActorSystem)
  extends LazyLogging with JsonMarshallSupport with XmlMarshallSupport {
  private var bind: ServerBinding = _

  def run()(implicit ctx: ExecutionContextExecutor): Future[Unit] = {
    implicit val mat: ActorMaterializer = ActorMaterializer()
    implicit val ctx: ExecutionContext = system.dispatcher
    implicit val config = ApplicationConfig.load()

    Http()
      .bindAndHandle(wrappedRootRoute, "0.0.0.0", port)
      .map { b =>
        synchronized(bind = b)
        ()
      }
  }

  def terminate()(implicit ctx: ExecutionContextExecutor): Future[Unit] = {
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

  def wrappedRootRoute(implicit mat: Materializer, ctx: ExecutionContext, conf: ApplicationConfig): Route = (HttpDirectives.timeoutHandler & HttpDirectives.baseResponseHeader) {
    errorHandleRoute
  }

  def errorHandleRoute(implicit mat: Materializer, ctx: ExecutionContext, conf: ApplicationConfig): Route = (
    handleRejections(HttpDirectives.rejectionHandler) &
    handleExceptions(HttpDirectives.exceptionHandler(logger))) {
      rootRoute
    }

  object MockAccountProvider extends AccountProvider {
    override def findAccessKey(accessKey: String): Future[Option[AccessCredential]] = {
      Future.successful(Some(AccessCredential("accessKey", "secret")))
    }
  }

  def rootRoute(implicit mat: Materializer, ctx: ExecutionContext, conf: ApplicationConfig): Route = {
    (HttpDirectives.extractAws4(MockAccountProvider) & extractRequest) { (key, req) =>
      logger.debug(s"Success Auth: $key Request: ${req.method.value} ${req.uri}")

      extractBucket(conf.domainSuffix) { bucket => // バケットに対しての操作
        logger.debug(s"Bucket: $bucket")
        (get & pathSingleSlash & parameters('prefix.?, 'marker.?)) { (prefix, maker) =>
          logger.debug(s"ListBucketResult bucket: $bucket")
          val contents = List(Content("example-file.txt", OffsetDateTime.now(), "0000", 123, "STANDARD"))
          complete(ListBucketResult(bucket, Some(""), Some(""), List(), contents, false))
        } ~ get {

          val target = req.uri.path.toString().replace("..", "")
          val path = Paths.get("./data" + target)
          val from = FileIO.fromPath(path)

          logger.debug(s"Response File content")
          complete(
            HttpResponse(
              entity = HttpEntity(
                ContentType(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`),
                Files.size(path),
                from)))
        } ~ (put & extractRequestEntity) { entity =>
          logger.debug(s"Upload Single Requiest")

          val target = req.uri.path.toString().replace("..", "")
          val to = FileIO.toPath(Paths.get("./data" + target))
          val res = entity.dataBytes.via(AwsSig4StreamStage.graph).runWith(to)
          onSuccess(res) { res =>
            complete("")
          }
        }
      } ~ (get & pathSingleSlash) { // サービスに対しての操作
        logger.debug(s"ListAllMyBucketsResult")
        complete(ListAllMyBucketsResult("owner", "UUID", List(Bucket("example-bucket", OffsetDateTime.now()))))
      }
    }
  }

  // .で区切られたドメインの最初の部分を返すだけ
  def extractBucket(domainSuffix: String): Directive1[String] = {
    extractUri
      .map(_.authority.host.address())
      .flatMap { host =>
        if (host.endsWith(domainSuffix)) provide(host.dropRight(domainSuffix.length))
        else reject()
      }
  }
}

case class ErrorResponse(error: String)

