package com.github.kamijin_fanta

import com.typesafe.config.ConfigFactory

case class ApplicationConfig(
  domainSuffix: String,
  role: String,
  proxyPort: Int,
  data: DataNodeConfig)

case class DataNodeConfig(
  port: Int,
  group: Int,
  node: Int,
  baseDir: String)

object ApplicationConfig {
  def load(): ApplicationConfig = {
    val conf = ConfigFactory.defaultApplication().getConfig("risa")

    val proxy = conf.getConfig("proxy")
    val data = conf.getConfig("data")

    val dataConfig = DataNodeConfig(
      port = data.getInt("port"),
      group = data.getInt("group"),
      node = data.getInt("node"),
      baseDir = data.getString("base-dire"))

    ApplicationConfig(
      domainSuffix = conf.getString("domain-suffix"),
      role = conf.getString("role"),
      proxyPort = proxy.getInt("port"),
      data = dataConfig)
  }
}
