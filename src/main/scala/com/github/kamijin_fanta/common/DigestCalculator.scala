package com.github.kamijin_fanta.common

import java.security.MessageDigest

import akka.stream.scaladsl.Sink
import akka.stream.stage.{ GraphStageLogic, GraphStageWithMaterializedValue, InHandler }
import akka.stream.{ Attributes, Inlet, SinkShape }
import akka.util.ByteString

import scala.concurrent.{ Future, Promise }

sealed trait Algorithm
object Algorithm {
  case object MD2 extends Algorithm
  case object MD5 extends Algorithm
  case object `SHA-1` extends Algorithm
  case object `SHA-256` extends Algorithm
  case object `SHA-384` extends Algorithm
  case object `SHA-512` extends Algorithm
}

case class DigestResult(byteArray: Array[Byte]) {
  val hex: String = byteArray.map("%02x" format _).mkString
}

object DigestCalculator {
  def fromAlgorithm(algorithm: Algorithm): Sink[ByteString, Future[DigestResult]] =
    Sink.fromGraph(new DigestCalculator(algorithm))
}

class DigestCalculator(algorithm: Algorithm) extends GraphStageWithMaterializedValue[SinkShape[ByteString], Future[DigestResult]] {
  val in: Inlet[ByteString] = Inlet("digestCalculator.in")

  override val shape: SinkShape[ByteString] = SinkShape(in)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[DigestResult]) = {

    val promise = Promise[DigestResult]()
    val stage = new GraphStageLogic(shape) {
      val digest = MessageDigest.getInstance(algorithm.toString)
      override def preStart(): Unit = {
        pull(in)
      }
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val arr = grab(in).toArray
          digest.update(arr)
          pull(in)
        }

        override def onUpstreamFinish(): Unit = {
          promise.success(DigestResult(digest.digest()))
        }
      })
    }

    (stage, promise.future)
  }
}
