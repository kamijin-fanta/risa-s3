package com.github.kamijin_fanta

import java.net.URLEncoder

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, extractRequest, extractRequestEntity, onSuccess, respondWithHeaders, withRequestTimeoutResponse}
import akka.http.scaladsl.server._
import com.github.kamijin_fanta.aws4.{AWS4Signer, AccessCredential, AccountProvider, AuthError}
import com.typesafe.scalalogging.Logger

object HttpDirectives extends JsonMarshallSupport {
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
      .handle {
        case ex: ValidationRejection =>
          complete((StatusCodes.BadRequest, ErrorResponse(ex.message)))
      }
      .result()
  }

  def timeoutResponse: HttpResponse = HttpResponse(
    StatusCodes.ServiceUnavailable,
    entity = writeJson(ErrorResponse("service unavailable")))

  def timeoutHandler: Directive0 = withRequestTimeoutResponse(_ => timeoutResponse)

  def baseResponseHeader: Directive0 = {
    import akka.http.scaladsl.model.headers.CacheDirectives._
    import akka.http.scaladsl.model.headers._
    respondWithHeaders(
      `Cache-Control`(`max-age`(10)),
      `Access-Control-Allow-Origin`.*)
  }

  def extractAws4(accountProvider: AccountProvider): Directive1[AccessCredential] = {
    (extractRequest & extractRequestEntity)
      .tflatMap {
        case (req, entity) =>
          val contentType = RawHeader("Content-Type", entity.contentType.toString())
          val headers = req.headers :+ contentType
          val accessKey = AWS4Signer.extractAccessKey(headers)
          onSuccess(accountProvider.findAccessKey(accessKey))
            .map(r => r.getOrElse(throw AuthError()))
            .map(key => {
              val queryString = req.uri.query()
                .sortBy(_._1)
                .map { case (k, v) => s"$k=${URLEncoder.encode(v, "UTF-8")}" }
                .mkString("&")

              AWS4Signer.validateSignature(headers, key.accessKey, key.accessKeySecret, req.method.value, req.uri.path.toString(), queryString)
              key
            })
      }
  }

}
