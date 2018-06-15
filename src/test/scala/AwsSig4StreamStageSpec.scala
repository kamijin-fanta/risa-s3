import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Source }
import akka.util.ByteString
import com.github.kamijin_fanta.aws4.AwsSig4StreamStage
import org.scalatest.{ BeforeAndAfterAll, FunSpec }

import scala.concurrent.Await
import scala.concurrent.duration._

class AwsSig4StreamStageSpec extends FunSpec with BeforeAndAfterAll {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ctx = system.dispatcher

  it("flow stage") {
    val sample =
      """
        |19;chunk-signature=db2c5725edb908fe5982e82a54e1f530b6fe16e4b011782e235dd56867338b5d
        |hogeeeeeeeeeeeee!!!!!!!!!
        |19;chunk-signature=db2c5725edb908fe5982e82a54e1f530b6fe16e4b011782e235dd56867338b5d
        |fugaaaaaaaaaaaaa!!!!!!!!!
        |0;chunk-signature=0d1fa3233921fbc5cfdac1f0a2f5d8b4e16f20753ac1b8d3122a4cde14026bf4
      """.stripMargin
    val source: Source[ByteString, NotUsed] = Source(ByteString(sample).sliding(10, 10).toList)
    val streamStage = Flow.fromGraph(new AwsSig4StreamStage())

    val probe = source
      .via(streamStage)
      .runFold(ByteString.empty) { case (acc, b) => acc ++ b }
      .map(_.utf8String)

    assert(Await.result(probe, 3.seconds) == "hogeeeeeeeeeeeee!!!!!!!!!fugaaaaaaaaaaaaa!!!!!!!!!")
  }

  override protected def afterAll(): Unit = {
    system.terminate()
  }
}
