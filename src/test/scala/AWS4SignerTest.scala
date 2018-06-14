import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import com.github.kamijin_fanta.aws4.{AWS4Signer, AuthError}
import org.scalatest.FunSpec

class AWS4SignerTest extends FunSpec {

  val headers: Seq[HttpHeader] = Seq(
    "Host: localhost:9555",
    "x-amz-content-sha256: e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "Authorization: AWS4-HMAC-SHA256 Credential=accessKey/20180613/us-east-1/s3/aws4_request, SignedHeaders=amz-sdk-invocation-id;amz-sdk-retry;content-type;host;user-agent;x-amz-content-sha256;x-amz-date, Signature=904dc402a76af23557c1f517698249bbe9d4d8fde5f889253dd7507137ce4b90",
    "X-Amz-Date: 20180613T032104Z",
    "User-Agent: aws-sdk-java/1.11.184 Windows_10/10.0 Java_HotSpot(TM)_64-Bit_Server_VM/25.131-b11/1.8.0_131 scala/2.12.3",
    "amz-sdk-invocation-id: 175e5fad-5e1f-18f3-a61e-653a20010875",
    "amz-sdk-retry: 0/0/500",
    "Content-Type: application/octet-stream",
    "Content-Length: 0",
    "Connection: Keep-Alive",
  )
    .map(s => s.split(": ").toList)
    .map { case List(key, value) => HttpHeader.parse(key, value) }
    .map(p => p.asInstanceOf[ParsingResult.Ok].header)

  it("canonicalRequest") {
    val signedHeaders = "amz-sdk-invocation-id;amz-sdk-retry;content-type;host;user-agent;x-amz-content-sha256;x-amz-date".split(";").toList
    val contentHash = headers.find(_.name() == "x-amz-content-sha256").map(_.value).getOrElse("")
    val collectedHeader = signedHeaders.flatMap(key => headers.find(h => h.name().toLowerCase == key)).map(h => s"${h.name.toLowerCase}:${h.value()}")
    val cannonicalReq = AWS4Signer.canonicalRequest("GET", "/", "", collectedHeader, signedHeaders, contentHash)

    val alg = "AWS4-HMAC-SHA256"
    val secret = "secret"
    val region = "us-east-1"
    val scope = "20180613/us-east-1/s3/aws4_request"
    val service = "s3"
    val amzDatetime = headers.find(_.name().toLowerCase == "x-amz-date").map(_.value()).getOrElse("")
    val amzDate = amzDatetime.substring(0, 8)
    val signing_key = AWS4Signer.genSignatureKey("secret", "20180613", "us-east-1", "s3")

    assert(AWS4Signer.formatHex(signing_key) == "67c0f6895ce15107cfdd4ff0490081d082df9fe05974d1ed02c0676a51b05247")

    val stringToSign = AWS4Signer.stringToSign(alg, amzDatetime, scope, cannonicalReq)
    val signeture = AWS4Signer.getSignature(signing_key, stringToSign)

    assert(signeture == "904dc402a76af23557c1f517698249bbe9d4d8fde5f889253dd7507137ce4b90")
  }


  it("calcSignature") {
    val secret = "secret"
    val method = "GET"
    val path = "/"
    val query = ""

    val signature = AWS4Signer.calcSignature(headers, secret, method, path, query)
    assert(signature == "904dc402a76af23557c1f517698249bbe9d4d8fde5f889253dd7507137ce4b90")

    AWS4Signer.validateSignature(headers, "accessKey", secret, method, path, query)
    assertThrows[AuthError] {
      AWS4Signer.validateSignature(headers, "invalid key", secret, method, path, query)
    }
    assertThrows[AuthError] {
      AWS4Signer.validateSignature(headers, "accessKey", secret + "invalid secret", method, path, query)
    }
  }
}
