package com.github.kamijin_fanta.common

import slick.jdbc

trait DbServiceComponent {
  def dbService: DbService

}
case class DbService(backend: jdbc.MySQLProfile.backend.Database) {

  def terminate(): Unit = {
    backend.close()
  }
}
