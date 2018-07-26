import java.io.{ ByteArrayInputStream, File }

import akka.actor.ActorSystem
import awscala.Region
import awscala.s3.{ Bucket, S3 }
import com.amazonaws.services.s3.model.{ CompleteMultipartUploadRequest, InitiateMultipartUploadRequest, PartETag, UploadPartRequest }
import com.github.kamijin_fanta.ApplicationConfig
import com.github.kamijin_fanta.proxy.RisaHttpProxyService
import org.scalatest.{ BeforeAndAfterAll, FunSpec, Matchers }

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContextExecutor
import scala.io.Source

class RisaHttpProxyServiceTest extends FunSpec with Matchers with BeforeAndAfterAll {
  implicit val system: ActorSystem = ActorSystem("test")
  implicit val ctx: ExecutionContextExecutor = system.dispatcher
  val port = 9555
  implicit val config: ApplicationConfig = ApplicationConfig.load().copy(proxyPort = port)
  var httpService: RisaHttpProxyService = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    httpService = RisaHttpProxyService(system)
    httpService.run()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    httpService.terminate()
    system.terminate()
  }

  describe("s3") {
    implicit val region = Region.Tokyo
    implicit val s3 = S3("accessKey", "secret")

    // *.local.dempa.moeで常に127.0.0.1を返すドメイン名
    s3.setEndpoint(s"http://local.dempa.moe:$port")

    val bucketName = "example-bucket"
    var bucket: Bucket = null

    it("bucket") {
      val _bucket: Option[Bucket] = s3.bucket(bucketName)
      assert(_bucket.isDefined)
      bucket = _bucket.get
    }
    it("list") {
      val list = bucket.ls("/")
      assert(list.nonEmpty)
      assert(list.head.right.get.bucket.name == "example-bucket")
    }
    it("put/get object") {
      bucket.putObject("example.txt", new File(getClass.getResource("./example.txt").getFile))

      val file = bucket.get("example.txt")
      assert(Source.fromInputStream(file.get.content).getLines().mkString("\n") == "hogeeeeeeeeeeeee!!!!!!!!!")
    }
    it("multipart") {
      val key = "multipart-example.txt"
      val multipartRequest = new InitiateMultipartUploadRequest(bucketName, key)
      val init = s3.initiateMultipartUpload(multipartRequest)

      for (index <- 1 to 9) {
        val content = index.toString * 50
        val stream = new ByteArrayInputStream(content.getBytes)
        val uploadRequest = new UploadPartRequest()
          .withBucketName(bucketName)
          .withUploadId(init.getUploadId)
          .withKey(key)
          .withPartNumber(index)
          .withPartSize(10)
          .withInputStream(stream)
        s3.uploadPart(uploadRequest)
      }
      val etag = List[PartETag]().asJava
      val completeRequest = new CompleteMultipartUploadRequest(bucketName, key, init.getUploadId, etag)
      s3.completeMultipartUpload(completeRequest)
    }
  }
}
