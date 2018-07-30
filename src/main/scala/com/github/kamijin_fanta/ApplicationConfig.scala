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
  baseDir: String,
  exposeHost: String)

object ApplicationConfig {
  def load(): ApplicationConfig = {
    val conf = ConfigFactory.defaultApplication().getConfig("risa")

    val proxy = conf.getConfig("proxy")
    val data = conf.getConfig("data")

    val dataConfig = DataNodeConfig(
      port = data.getInt("port"),
      group = data.getInt("group"),
      baseDir = data.getString("base-dire"),
      exposeHost = data.getString("expose-host"))

    ApplicationConfig(
      domainSuffix = conf.getString("domain-suffix"),
      role = conf.getString("role"),
      proxyPort = proxy.getInt("port"),
      data = dataConfig)
  }
}
