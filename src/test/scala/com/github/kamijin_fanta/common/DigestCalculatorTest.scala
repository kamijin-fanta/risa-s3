package com.github.kamijin_fanta.common

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.{ AsyncFunSuite, BeforeAndAfterAll }

import scala.concurrent.Await
import scala.concurrent.duration._

class DigestCalculatorTest extends AsyncFunSuite with BeforeAndAfterAll {
  implicit val system = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()

  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 5 seconds)
  }

  test("sink test") {
    val sink = DigestCalculator.fromAlgorithm(Algorithm.`SHA-256`)
    val source = Source(List("a", "b", "c", "d").map(s => ByteString(s)))
    val res = source runWith sink

    res.map {
      digest => assert(digest.hex === "88d4266fd4e6338d13b845fcf289579d209c897823b9217da3e161936f031589")
    }
  }
}

