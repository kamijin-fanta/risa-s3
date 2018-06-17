import java.io.File

import akka.actor.ActorSystem
import awscala.Region
import awscala.s3.{ Bucket, S3 }
import com.github.kamijin_fanta.RisaHttpService
import org.scalatest.{ BeforeAndAfterAll, FunSpec, Matchers }

import scala.concurrent.ExecutionContextExecutor
import scala.io.Source

class RisaHttpServiceTest extends FunSpec with Matchers with BeforeAndAfterAll {
  implicit val system: ActorSystem = ActorSystem("test")
  implicit val ctx: ExecutionContextExecutor = system.dispatcher
  val port = 9555
  var httpService: RisaHttpService = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    httpService = RisaHttpService(port)
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

    it("bucket") {
      val bucket: Option[Bucket] = s3.bucket("example-bucket")
      assert(bucket.isDefined)

      val list = bucket.get.ls("/")
      println(list.toList)

      bucket.get.putObject("example.txt", new File(getClass.getResource("./example.txt").getFile))

      val file = bucket.get.get("example.txt")
      println(Source.fromInputStream(file.get.content).getLines().mkString("\n"))
    }
  }
}
