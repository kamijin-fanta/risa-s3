import akka.actor.ActorSystem
import awscala.Region
import awscala.s3.S3
import com.github.kamijin_fanta.RisaHttpService
import org.scalatest.{ BeforeAndAfterAll, FunSpec, Matchers }

import scala.concurrent.ExecutionContextExecutor

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
    it("bucket") {
      implicit val region = Region.Tokyo
      val s3 = S3("accessKey", "secret")
      s3.setEndpoint(s"http://localhost:${port}")
      val b = s3.bucket("example-bucket")
      println(s"Bucket: $b")
    }
  }
}
