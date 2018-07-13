import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.Materializer
import akka.stream.scaladsl.{ BroadcastHub, Keep, Sink, Source }
import com.github.kamijin_fanta.ApplicationConfig
import com.github.kamijin_fanta.common.model.DataNode
import com.github.kamijin_fanta.data.RisaHttpDataService
import com.github.kamijin_fanta.data.metaProvider.LocalMetaBackendService
import org.scalatest.{ BeforeAndAfterAll, FunSpec }

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, ExecutionContextExecutor, Future }

class RisaHttpDataServiceTest extends FunSpec with BeforeAndAfterAll with ScalatestRouteTest {
  implicit val ctx: ExecutionContextExecutor = system.dispatcher
  val port = 9556
  implicit val config: ApplicationConfig = {
    val app = ApplicationConfig.load()
    app.copy(data = app.data.copy(port = port))
  }
  var httpService: RisaHttpDataService = _
  var httpService2: RisaHttpDataService = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    val nodeList = Seq(
      DataNode("DC-A-0001", "1", s"localhost:${port}"),
      DataNode("DC-A-0001", "2", s"localhost:${port + 1}"))
    httpService = new RisaHttpDataService() {
      override def metaBackendService: LocalMetaBackendService =
        new LocalMetaBackendService() {
          override def nodes(nodeGroup: String): Future[Seq[DataNode]] = {
            Future.successful(nodeList)
          }
        }
    }
    val config2 = config.copy(data = config.data.copy(port = port + 1, baseDir = "./data2"))
    httpService2 = new RisaHttpDataService()(config2, system) {
      override def metaBackendService: LocalMetaBackendService =
        new LocalMetaBackendService() {
          override def nodes(nodeGroup: String): Future[Seq[DataNode]] = {
            Future.successful(nodeList)
          }
        }
    }
    httpService.run()
    httpService2.run()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    httpService.terminate()
    httpService2.terminate()
    system.terminate()
  }

  val base = Uri.from(scheme = "http", host = "localhost", port = port)

  def blockingRequest(httpRequest: HttpRequest, timeout: Duration = 2 seconds): HttpResponse = {
    Await.result(Http().singleRequest(httpRequest), timeout)
  }

  def blockingToStrictString(responseEntity: ResponseEntity, timeout: FiniteDuration = 2 seconds)(implicit ec: ExecutionContext, fm: Materializer): String = {
    Await.result(responseEntity.toStrict(timeout).map(_.data.utf8String), 1 seconds)
  }

  it("upload / get / delete") {
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

  it("stream test") {
    val rawSource = Source(1 to 5).runWith(BroadcastHub.sink(128))
    assert(Await.result(rawSource.delay(1 second).runWith(Sink.seq), 10 seconds) == Vector())

    val deleySource = Source(1 to 5).delay(1 micro).runWith(BroadcastHub.sink(128))
    assert(Await.result(deleySource.delay(1 second).runWith(Sink.seq), 10 seconds) == (1 to 5))
  }

  it("internal post") {
    val tablet = "0001"
    val name = "internal-post-data"
    val path = Path(s"/internal/object/$tablet/$name")

    val dummyContent = "a" * 100
    val dummyEntity = HttpEntity(dummyContent)
    val upRes = blockingRequest(HttpRequest(
      HttpMethods.POST,
      uri = base.withPath(path), entity = dummyEntity))

    assert(upRes.status.isSuccess())

    val getRes = blockingRequest(HttpRequest(uri = base.withPath(path)))
    val getEntity = blockingToStrictString(getRes.entity)
    assert(dummyContent == getEntity)

    val delRes = blockingRequest(HttpRequest(uri = base.withPath(path), method = HttpMethods.DELETE))
    assert(delRes.status.isSuccess())

    val delRes2 = blockingRequest(HttpRequest(uri = base.withPath(path), method = HttpMethods.DELETE))
    assert(delRes2.status === StatusCodes.NotFound)
  }
}
