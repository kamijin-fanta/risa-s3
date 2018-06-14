package com.github.kamijin_fanta

import java.time.OffsetDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.github.kamijin_fanta.aws4.{AccessCredential, AccountProvider}
import com.github.kamijin_fanta.response.{Bucket, ListAllMyBucketsResult}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContextExecutor, Future}

case class RisaHttpService(port: Int)(implicit system: ActorSystem)
  extends LazyLogging with JsonMarshallSupport with XmlMarshallSupport {
  private var bind: ServerBinding = _

  def run()(implicit ctx: ExecutionContextExecutor): Future[Unit] = {
    implicit val mat: ActorMaterializer = ActorMaterializer()

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

  def wrappedRootRoute: Route = (HttpDirectives.timeoutHandler & HttpDirectives.baseResponseHeader) {
    errorHandleRoute
  }

  def errorHandleRoute: Route = (
    handleRejections(HttpDirectives.rejectionHandler) &
    handleExceptions(HttpDirectives.exceptionHandler(logger))) {
      rootRoute
    }

  object MockAccountProvider extends AccountProvider {
    override def findAccessKey(accessKey: String): Future[Option[AccessCredential]] = {
      Future.successful(Some(AccessCredential("accessKey", "secret")))
    }
  }

  def rootRoute: Route = {
    HttpDirectives.extractAws4(MockAccountProvider) { key =>
      logger.debug("AUTH! " + key)
      (pathSingleSlash & extractUri & extractBucket & extractRequest) { (uri, bucket, req) =>
        logger.debug(s"path: ${req.uri}")
        logger.debug(s"bucket: $bucket auth: $key ")
        complete("OK!")
        //        complete(ListBucketResult(bucket, None, Some("/"), List(), List(), true))
        complete(ListAllMyBucketsResult("owner", "UUID", List(Bucket("example-bucket", OffsetDateTime.now()))))
      } ~ {
        get {
          extractRequest { req =>
            logger.debug("##### list of bucket")
            logger.debug(req.uri.toString)
            logger.debug(req.headers.toString)

            complete("OK!")
          }
        }
      }
    }
  }

  // .で区切られたドメインの最初の部分を返すだけ
  def extractBucket: Directive1[String] = {
    extractUri
      .map(_.authority.host.address())
      .map(_.split('.').head)
  }
}

case class ErrorResponse(error: String)
