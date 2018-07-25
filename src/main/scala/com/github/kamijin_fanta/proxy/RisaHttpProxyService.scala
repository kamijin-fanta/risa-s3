package com.github.kamijin_fanta.proxy

import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.time.OffsetDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.{ FileIO, Source }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.ByteString
import com.github.kamijin_fanta.ApplicationConfig
import com.github.kamijin_fanta.aws4.{ AccessCredential, AccountProvider, AwsSig4StreamStage }
import com.github.kamijin_fanta.common.{ ActorSystemServiceComponent, TerminableService }
import com.github.kamijin_fanta.response._
import com.typesafe.scalalogging.{ LazyLogging, Logger }

import scala.compat.java8.StreamConverters._
import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }

case class RisaHttpProxyService(_system: ActorSystem)(implicit applicationConfig: ApplicationConfig)
  extends LazyLogging
  with JsonMarshallSupport
  with XmlMarshallSupport
  with TerminableService
  with ActorSystemServiceComponent {
  private var bind: ServerBinding = _

  override implicit val actorSystem: ActorSystem = _system

  override def run()(implicit ctx: ExecutionContextExecutor): Future[Unit] = {
    implicit val mat: ActorMaterializer = ActorMaterializer()
    implicit val ctx: ExecutionContext = actorSystem.dispatcher

    Http()
      .bindAndHandle(wrappedRootRoute, "0.0.0.0", applicationConfig.proxyPort)
      .map { b =>
        synchronized(bind = b)
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

  def wrappedRootRoute(implicit mat: Materializer, ctx: ExecutionContext): Route =
    (HttpDirectives.timeoutHandler & HttpDirectives.baseResponseHeader) {
      errorHandleRoute
    }

  def errorHandleRoute(implicit mat: Materializer, ctx: ExecutionContext): Route = (
    handleRejections(HttpDirectives.rejectionHandler) &
    handleExceptions(HttpDirectives.exceptionHandler(logger))) {
      RisaHttpProxyService.rootRoute(MockAccountProvider, logger)
    }

  object MockAccountProvider extends AccountProvider {
    override def findAccessKey(accessKey: String): Future[Option[AccessCredential]] = {
      Future.successful(Some(AccessCredential("accessKey", "secret")))
    }
  }

}

object RisaHttpProxyService extends JsonMarshallSupport with XmlMarshallSupport {

  def rootRoute(accountProvider: AccountProvider, logger: Logger)(implicit mat: Materializer, ctx: ExecutionContext, conf: ApplicationConfig): Route = {
    (HttpDirectives.extractAws4(accountProvider) & extractRequest) { (key, req) =>
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
        } ~ (put & extractRequestEntity & parameters('partNumber.?, 'uploadId.?)) { (entity, partNum, uploadId) =>
          logger.debug(s"Upload Single Request")

          val multipart = for {
            num <- partNum
            id <- uploadId
          } yield (num, id)

          val target = multipart match {
            case Some((num, id)) =>
              // like: /tmp/UPLOAD_ID/0000000001
              "/tmp/" + id.replace("..", "") + "/" + ("0" * (10 - num.length)) + num
            case None => req.uri.path.toString().replace("..", "")
          }
          val to = FileIO.toPath(Paths.get("./data" + target))
          val res = entity.dataBytes.via(AwsSig4StreamStage.graph).runWith(to)
          onSuccess(res) { res =>
            complete("")
          }
        } ~ (post & parameter('uploads)) { u =>
          logger.debug(s"Start multipart upload")
          val uploadId = System.currentTimeMillis().toString
          Files.createDirectories(Paths.get("./data/tmp/" + uploadId))

          complete(InitiateMultipartUploadResult(bucket, req.uri.path.toString(), uploadId))
        } ~ (post & parameter('uploadId)) { uploadId =>
          logger.debug(s"Complete multipart upload")

          // todo etag

          val dir = "./data/tmp/" + (uploadId.replace("..", ""))
          val key = req.uri.path.toString().replace("..", "")

          val fileList = Files.list(Paths.get(dir + "/"))
          val sorted = fileList.toScala[Seq].sortBy(_.getFileName)
          val sources = sorted.map(p => FileIO.fromPath(p)).toList
          val concat = Source.zipN[ByteString](sources).flatMapConcat(Source.apply)

          val toPath = Paths.get("./data/" + key)
          val toFile = FileIO.toPath(toPath)

          val res = concat.runWith(toFile)
          onSuccess(res) { ioRes =>
            val visitor = new SimpleFileVisitor[Path] {

              override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
                Files.delete(dir)
                FileVisitResult.CONTINUE
              }

              override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
                Files.delete(file)
                FileVisitResult.CONTINUE
              }
            }
            Files.walkFileTree(Paths.get(dir), visitor)
            complete(CompleteMultipartUploadResult(s"http://$bucket${conf.domainSuffix}$key", bucket, key, "ETAG"))
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

