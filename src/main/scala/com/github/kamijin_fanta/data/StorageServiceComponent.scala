package com.github.kamijin_fanta.data

import java.nio.file.{ Files, NoSuchFileException, Path, Paths }

import akka.stream.{ IOResult, Materializer }
import akka.stream.scaladsl.{ FileIO, Sink, Source }
import akka.util.ByteString
import com.github.kamijin_fanta.common.{ Algorithm, ApplicationConfigComponent, DigestCalculator, DigestResult }
import com.github.kamijin_fanta.data.model.TabletItem

import scala.concurrent.{ ExecutionContextExecutor, Future, blocking }
import scala.collection.JavaConverters._
import scala.util.Try

trait StorageServiceComponent {
  self: ApplicationConfigComponent =>

  def storageService: StorageService

  class StorageService {
    implicit class RichTabletItem(tabletItem: TabletItem) {
      def toPath: Path = {
        val normalize = (str: String) => str.replace("..", "")
        Paths.get(
          applicationConfig.data.baseDir,
          normalize(tabletItem.tablet),
          normalize(tabletItem.itemName))
      }
    }

    def readFile(tabletItem: TabletItem): Source[ByteString, Future[IOResult]] =
      FileIO.fromPath(tabletItem.toPath)
    def writeFile(tabletItem: TabletItem): Sink[ByteString, Future[IOResult]] =
      FileIO.toPath(tabletItem.toPath)
    def deleteFile(tabletItem: TabletItem): Unit =
      Files.delete(tabletItem.toPath)

    def getFileSize(tabletItem: TabletItem): Long =
      Files.size(tabletItem.toPath)
    def calcChecksum(tabletItem: TabletItem)(implicit materializer: Materializer): Future[DigestResult] = {
      readFile(tabletItem) runWith DigestCalculator.fromAlgorithm(Algorithm.`SHA-256`)
    }

    def toPath(tabletItem: TabletItem): Path =
      tabletItem.toPath

    private val nodeIdPath = Paths.get(
      applicationConfig.data.baseDir,
      "_node_id")
    def readNodeId()(implicit ctx: ExecutionContextExecutor): Future[Option[Int]] = {
      Future {
        blocking {
          try {
            Files
              .readAllLines(nodeIdPath)
              .asScala
              .headOption
              .flatMap(i => Try {
                i.toInt
              }.toOption)
          } catch {
            case _: NoSuchFileException =>
              None
          }
        }
      }
    }
    def writeNodeId(nodeId: Int)(implicit ctx: ExecutionContextExecutor): Future[Path] = {
      Future {
        blocking {
          Files.write(nodeIdPath, nodeId.toString.getBytes)
        }
      }
    }
  }
}
