package com.github.kamijin_fanta.data.metaProvider

import com.github.kamijin_fanta.ApplicationConfig
import com.github.kamijin_fanta.common.DbService
import com.github.kamijin_fanta.common.model.DataNode
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ ExecutionContext, Future }

trait MetaBackendServiceComponent {
  def metaBackendService: LocalMetaBackendService
}

class LocalMetaBackendService(dbService: DbService)(implicit ctx: ExecutionContext) {
  // todo refactor NodeGroup is Int

  def nodes(nodeGroup: String): Future[Seq[DataNode]] = {
    Future.successful(Seq(
      DataNode("DC-A-0001", "1", "localhost:9551"),
      DataNode("DC-A-0001", "2", "localhost:9552")))
  }

  def otherNodes(nodeGroup: String, selfNodeId: String): Future[Seq[DataNode]] = {
    nodes(nodeGroup).map(_.filterNot(_.nodeId == selfNodeId))
  }
  def otherNodes()(implicit applicationConfig: ApplicationConfig): Future[Seq[DataNode]] = {
    otherNodes(applicationConfig.data.group, applicationConfig.data.node)
  }

  def addItem(volumeGroup: Int, tablet: String, name: String, hash: String): Future[Int] = {
    val newData = Tables.VolumeFileRow(volumeGroup, tablet, name, hash)
    dbService.backend.run(Tables.VolumeFile += newData)
  }
}
