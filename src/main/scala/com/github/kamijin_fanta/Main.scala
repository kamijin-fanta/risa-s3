package com.github.kamijin_fanta

import akka.actor.ActorSystem
import com.github.kamijin_fanta.common.TerminableService
import com.github.kamijin_fanta.data.RisaHttpDataService
import com.github.kamijin_fanta.proxy.RisaHttpProxyService

import scala.concurrent.ExecutionContextExecutor
import scala.util.{ Failure, Success }

object Main {
  var stop = false
  implicit val system: ActorSystem = ActorSystem()
  implicit val ctx: ExecutionContextExecutor = system.dispatcher

  def main(args: Array[String]): Unit = {
    try {
      implicit val config: ApplicationConfig = ApplicationConfig.load()
      val httpService: TerminableService = config.role match {
        case "proxy" => RisaHttpProxyService(system)
        case "data" => RisaHttpDataService(system)
        case x => throw new IllegalArgumentException(s"known role $x")
      }

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
