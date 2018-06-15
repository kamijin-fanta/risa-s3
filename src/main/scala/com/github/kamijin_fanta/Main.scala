package com.github.kamijin_fanta

import akka.actor.ActorSystem

import scala.concurrent.ExecutionContextExecutor
import scala.util.{ Failure, Success }

object Main {
  var stop = false
  implicit val system: ActorSystem = ActorSystem()
  implicit val ctx: ExecutionContextExecutor = system.dispatcher

  def main(args: Array[String]): Unit = {
    try {
      val httpService = RisaHttpService(9550)

      httpService.run().onComplete {
        case Success(_) =>
        case Failure(th) =>
          th.printStackTrace()
          shutdown()
      }

      println("Press RETURN to stop...")
      while (!stop && System.in != null) {
        if (System.in.available() != 0) {
          System.in.read() match {
            case 13 | 10 =>
              shutdown()
            case _ =>
          }
        }
        Thread.sleep(100)
      }
    } catch {
      case th: Throwable =>
        th.printStackTrace()
        shutdown()
    }
  }

  def shutdown(): Unit = {
    stop = true
    system.terminate()
  }
}
