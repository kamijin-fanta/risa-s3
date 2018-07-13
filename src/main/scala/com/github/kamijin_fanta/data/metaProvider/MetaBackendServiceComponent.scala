package com.github.kamijin_fanta.data.metaProvider

import com.github.kamijin_fanta.common.model.DataNode

import scala.concurrent.{ ExecutionContext, Future }

trait MetaBackendServiceComponent {
  def metaBackendService: LocalMetaBackendService
}

class LocalMetaBackendService(implicit ctx: ExecutionContext) {
  def nodes(nodeGroup: String): Future[Seq[DataNode]] = {
    Future.successful(Seq(
      DataNode("DC-A-0001", "1", "localhost:9551"),
      DataNode("DC-A-0001", "2", "localhost:9552")))
  }

  def otherNodes(nodeGroup: String, selfNodeId: String): Future[Seq[DataNode]] = {
    nodes(nodeGroup).map(_.filterNot(_.nodeId == selfNodeId))
  }
}
