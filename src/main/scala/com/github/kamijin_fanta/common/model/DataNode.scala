package com.github.kamijin_fanta.common.model

import akka.http.scaladsl.model.Uri

case class DataNode(nodeGroup: String, nodeId: String, address: String) {
  val apiBaseUri = Uri(s"http://$address")
}
