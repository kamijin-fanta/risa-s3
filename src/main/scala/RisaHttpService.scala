import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import aws4.{ AccessCredential, AccountProvider }
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContextExecutor, Future }

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
      pathPrefix(Segments) { bucket =>
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

case class ErrorResponse(error: String)
