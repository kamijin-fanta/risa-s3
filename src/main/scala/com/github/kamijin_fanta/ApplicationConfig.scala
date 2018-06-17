package com.github.kamijin_fanta

import com.typesafe.config.ConfigFactory

case class ApplicationConfig(
                              domainSuffix: String,
)

object ApplicationConfig {
  def load(): ApplicationConfig = {
    val conf = ConfigFactory.defaultApplication().getConfig("risa")

    ApplicationConfig(
      domainSuffix = conf.getString("domain-suffix")
    )
  }
}