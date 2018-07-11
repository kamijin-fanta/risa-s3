package com.github.kamijin_fanta.data.metaProvider

import com.github.kamijin_fanta.common.model.DataNode

class LocalMetaBackend {
  def nodes(nodeGroup: String): Seq[DataNode] = {
    Seq(
      DataNode("DC-A-0001", "1", "localhost:9551"))
  }

  def otherNodes(nodeGroup: String, selfNodeId: String): Seq[DataNode] = {
    nodes(nodeGroup).filter(_.nodeId == selfNodeId)
  }
}
