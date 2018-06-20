package com.github.kamijin_fanta.aws4

import akka.stream.scaladsl.Flow
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream.{ Attributes, FlowShape, Inlet, Outlet }
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable

object AwsSig4StreamStage {
  val graph = Flow.fromGraph(new AwsSig4StreamStage)
}
class AwsSig4StreamStage extends GraphStage[FlowShape[ByteString, ByteString]] with LazyLogging {
  val in = Inlet[ByteString]("in")
  val out = Outlet[ByteString]("out")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val hexChars = "0123456789abcdef".getBytes.toSet
    val headerBuffer = mutable.ArrayBuffer[Byte]()
    var remainingOfByte = 0

    setHandler(in, new InHandler {
      def collectBuffer(byteString: ByteString): ByteString = {
        // ループを回してBodyのByteStringをかき集める　Headerがあったらその分送る
        var pos = 0
        val bodyBuffer = mutable.ArrayBuffer[Byte]()

        while (byteString.length > pos) {
          val current = byteString.drop(pos)
          if (remainingOfByte == 0) { // find headers
            val found = current.indexOf('\n'.toByte)
            val splitPoint =
              if (found == -1) Int.MaxValue
              else found + 1
            val taken = current.take(splitPoint)
            pos += taken.length
            headerBuffer.appendAll(taken)
            taken.toBuffer

            if (found != -1) { // \nが有った
              val hexSize = headerBuffer.dropWhile(s => !hexChars.contains(s)).takeWhile(hexChars.contains)
              if (hexSize.nonEmpty && hexSize.length <= 8) {
                remainingOfByte += Integer.parseInt(new String(hexSize.toArray), 16)
              }
              headerBuffer.clear()
            }
          } else { // collect body
            val taken = current.take(remainingOfByte)
            pos += taken.length
            remainingOfByte -= taken.length
            bodyBuffer ++= taken
          }
        }
        ByteString(bodyBuffer.toArray)
      }

      override def onPush(): Unit = {
        val elem = grab(in)
        val collect = collectBuffer(elem)
        push(out, collect)
      }
    })
    setHandler(out, new OutHandler {
      override def onPull(): Unit = pull(in)
    })
  }

  override def shape: FlowShape[ByteString, ByteString] = FlowShape(in, out)
}
