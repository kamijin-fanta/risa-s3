package com.github.kamijin_fanta.data

import java.nio.file.{ Files, Path, Paths }

import akka.stream.{ IOResult, Materializer }
import akka.stream.scaladsl.{ FileIO, Sink, Source }
import akka.util.ByteString
import com.github.kamijin_fanta.common.{ Algorithm, ApplicationConfigComponent, DigestCalculator, DigestResult }
import com.github.kamijin_fanta.data.model.TabletItem

import scala.concurrent.Future

trait StorageServiceComponent {
  self: ApplicationConfigComponent =>

  def storageService: StorageService

  trait StorageService {
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
      Files.deleteIfExists(tabletItem.toPath)

    def getFileSize(tabletItem: TabletItem): Long =
      Files.size(tabletItem.toPath)
    def calcChecksum(tabletItem: TabletItem)(implicit materializer: Materializer): Future[DigestResult] = {
      readFile(tabletItem) runWith DigestCalculator.fromAlgorithm(Algorithm.`SHA-256`)
    }
  }
}
