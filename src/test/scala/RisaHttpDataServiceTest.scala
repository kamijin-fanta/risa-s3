import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.Materializer
import com.github.kamijin_fanta.ApplicationConfig
import com.github.kamijin_fanta.data.RisaHttpDataService
import org.scalatest.{ BeforeAndAfterAll, FunSpec }

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, ExecutionContextExecutor }

class RisaHttpDataServiceTest extends FunSpec with BeforeAndAfterAll with ScalatestRouteTest {
  implicit val ctx: ExecutionContextExecutor = system.dispatcher
  val port = 9556
  implicit val config: ApplicationConfig = {
    val app = ApplicationConfig.load()
    app.copy(data = app.data.copy(port = port))
  }
  var httpService: RisaHttpDataService = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    httpService = RisaHttpDataService()
    httpService.run()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    httpService.terminate()
    system.terminate()
  }

  def blockingRequest(httpRequest: HttpRequest, timeout: Duration = 2 seconds): HttpResponse = {
    Await.result(Http().singleRequest(httpRequest), timeout)
  }
  def blockingToStrictString(responseEntity: ResponseEntity, timeout: FiniteDuration = 2 seconds)(implicit ec: ExecutionContext, fm: Materializer): String = {
    Await.result(responseEntity.toStrict(timeout).map(_.data.utf8String), 1 seconds)
  }

  it("upload / get / delete") {
    val base = Uri.from(scheme = "http", host = "localhost", port = port)
    val dummyContent = "a" * 100

    val uploadEntity = HttpEntity(dummyContent)
    val uploadReq = HttpRequest(HttpMethods.POST, base.withPath(Path("/object")), entity = uploadEntity)
    val uploadRes = blockingRequest(uploadReq)
    assert(uploadRes.status.isSuccess(), "file uploading is fail")

    val storedPath = blockingToStrictString(uploadRes.entity)
    val objectPath = Path(s"/object$storedPath")

    val getRes = blockingRequest(HttpRequest(uri = base.withPath(objectPath)))
    val getEntity = blockingToStrictString(getRes.entity)
    assert(dummyContent == getEntity)

    val delRes = blockingRequest(HttpRequest(uri = base.withPath(objectPath), method = HttpMethods.DELETE))
    assert(delRes.status.isSuccess())

    val delRes2 = blockingRequest(HttpRequest(uri = base.withPath(objectPath), method = HttpMethods.DELETE))
    assert(delRes2.status === StatusCodes.NotFound)
  }
}
