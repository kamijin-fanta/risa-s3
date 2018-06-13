import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import aws4.{AWS4Signer, AuthError}
import com.typesafe.scalalogging.{LazyLogging, Logger}

import scala.concurrent.{ExecutionContextExecutor, Future}

case class RisaHttpService(port: Int)(implicit system: ActorSystem) extends LazyLogging with JsonMarshallSupport {
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


  def wrappedRootRoute: Route = (RoutesUtils.timeoutHandler & RoutesUtils.baseResponseHeader) {
    errorHandleRoute
  }

  def errorHandleRoute: Route = (
    handleRejections(RoutesUtils.rejectionHandler) &
      handleExceptions(RoutesUtils.exceptionHandler(logger))) {
    rootRoute
  }
  val userProvider = (accesskey: String) => {
    logger.debug(s"accesskey $accesskey")
    Future.successful(Some(AccessKey("accessKey", "secret")))
  }

  def rootRoute: Route = {
    RoutesUtils.extractAws4(userProvider) { key =>
      logger.debug("AUTH! " + key)
      pathPrefix(Segment) { bucket =>
        logger.debug("buckets: " + bucket)
        complete("OK!")
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
}

case class AccessKey(key: String, secret: String)

case class ErrorResponse(error: String)

object RoutesUtils extends JsonMarshallSupport {
  def exceptionHandler(logger: Logger) = ExceptionHandler {
    case th: Throwable =>
      logger.warn("Internal Server Error", th)
      complete((StatusCodes.InternalServerError, ErrorResponse("Internal Server Error")))
  }

  def rejectionHandler: RejectionHandler = {
    RejectionHandler.newBuilder()
      .handleNotFound {
        complete((StatusCodes.NotFound, ErrorResponse("Not Found Endpoint")))
      }
      .handle { case ex: ValidationRejection =>
        complete((StatusCodes.BadRequest, ErrorResponse(ex.message)))
      }
      .result()
  }

  def timeoutResponse: HttpResponse = HttpResponse(
    StatusCodes.ServiceUnavailable,
    entity = writeJson(ErrorResponse("service unavailable"))
  )

  def timeoutHandler: Directive0 = withRequestTimeoutResponse(_ => timeoutResponse)

  def baseResponseHeader: Directive0 = {
    import akka.http.scaladsl.model.headers.CacheDirectives._
    import akka.http.scaladsl.model.headers._
    respondWithHeaders(
      `Cache-Control`(`max-age`(10)),
      `Access-Control-Allow-Origin`.*
    )
  }

  def extractAws4(userProvider: String => Future[Option[AccessKey]]): Directive1[AccessKey] = {
    (extractRequest & extractRequestEntity)
      .tflatMap { case (req, entity) =>
        val contentType = RawHeader("Content-Type", entity.contentType.toString())
        val headers = req.headers :+ contentType
        val accessKey = AWS4Signer.extractAccessKey(headers)
        onSuccess(userProvider(accessKey))
          .map(r => r.getOrElse(throw AuthError()))
          .map(key => {
            AWS4Signer.validateSignature(headers, key.key, key.secret, req.method.value, req.uri.path.toString(), req.uri.rawQueryString.getOrElse(""))
            key
          })
      }
  }

}
