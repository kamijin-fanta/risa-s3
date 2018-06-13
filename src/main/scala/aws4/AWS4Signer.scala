package aws4

import java.security.MessageDigest

import akka.http.scaladsl.model.HttpHeader
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object AWS4Signer {
  def formatHex(bytes: Array[Byte]) = bytes.map("%02x".format(_)).mkString

  def sign(secrets: Array[Byte], message: String): Array[Byte] = {
    val secret = new SecretKeySpec(secrets, "HmacSHA256")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(secret)
    mac.doFinal(message.getBytes)
  }

  private val sha256Instance = MessageDigest.getInstance("SHA-256")

  def sha256(message: String): String = {
    sha256Instance.digest(message.getBytes())
      .map("%02x".format(_)).mkString
  }

  def genSignatureKey(key: String, date: String, region: String, service: String): Array[Byte] = {
    val signedDate = sign(("AWS4" + key).getBytes, date)
    val signedRegion = sign(signedDate, region)
    val signedService = sign(signedRegion, service)
    sign(signedService, "aws4_request")
  }

  def canonicalRequest(method: String, path: String, query: String, headers: Seq[String], signedHeaders: Seq[String], payloadHash: String): String = {
    Seq(method, path, query, headers.mkString("\n"), "", signedHeaders.mkString(";"), payloadHash).mkString("\n")
  }

  def stringToSign(algorithm: String, datetime: String, scope: String, canonicalRequest: String): String = {
    Seq(algorithm, datetime, scope, sha256(canonicalRequest)).mkString("\n")
  }

  def getSignature(signatureKey: Array[Byte], stringToSign: String): String = {
    formatHex(sign(signatureKey, stringToSign))
  }

  def authorizationHeader(headers: Seq[HttpHeader]): AwsAuthorizationHeader = {
    val authorizationHeaderValue = headers
      .find(_.name().toLowerCase == "authorization")
      .map(_.value())
      .getOrElse(throw NotFoundHeader("authorization"))
    AwsAuthorizationHeaderParser(authorizationHeaderValue).getOrElse(throw FailedParseHeader("authorization"))
  }

  def calcSignature(headers: Seq[HttpHeader], secretAccessKey: String, method: String, path: String, query: String): String = {
    val auth = authorizationHeader(headers)

    val canonicalHeader = auth.signedHeaders
      .flatMap(key => headers.find(h => h.name().toLowerCase == key))
      .map(h => s"${h.name.toLowerCase}:${h.value()}")
    val contentHash = headers.find(_.name() == "x-amz-content-sha256").map(_.value).getOrElse(throw NotFoundHeader("x-amz-content-sha256"))
    val canonicalReq = AWS4Signer.canonicalRequest(method, path, query, canonicalHeader, auth.signedHeaders, contentHash)

    val amzDatetime = headers.find(_.name().toLowerCase == "x-amz-date").map(_.value()).getOrElse("")
    val amzDate = amzDatetime.substring(0, 8)

    val signingKey = AWS4Signer.genSignatureKey(secretAccessKey, amzDate, auth.credential.region, auth.credential.service)
    val stringToSign = AWS4Signer.stringToSign(auth.algorithm, amzDatetime, auth.credential.scope, canonicalReq)

    val signature = AWS4Signer.getSignature(signingKey, stringToSign)
    signature
  }

  def validateSignature(headers: Seq[HttpHeader], accessKey: String, secretAccessKey: String, method: String, path: String, query: String): Unit = {
    val signature = calcSignature(headers, secretAccessKey, method, path, query)
    val auth = authorizationHeader(headers)
    if (accessKey != auth.credential.key || signature != auth.signature) {
      throw AuthError()
    }
    ()
  }

  def extractAccessKey(headers: Seq[HttpHeader]): String = {
    val auth = authorizationHeader(headers)
    auth.credential.key
  }
}
