package com.github.kamijin_fanta.data

import java.io.File

import akka.NotUsed
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.github.kamijin_fanta.common.ApplicationConfigComponent
import com.github.kamijin_fanta.data.model.TabletItem

import scala.concurrent.Future

trait StorageServiceComponent {
  self: ApplicationConfigComponent =>

  def storageService: StorageService

  trait StorageService {
    def readFile(tabletItem: TabletItem): Source[ByteString, NotUsed]
    def writeFile(tabletItem: TabletItem): Sink[ByteString, NotUsed]
    def getFile(tabletItem: TabletItem): File
    def calcChecksum(tabletItem: TabletItem): Future[String]
  }
}
