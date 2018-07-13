package com.github.kamijin_fanta.common

import slick.jdbc

trait DbServiceComponent {
  def dbService: DbService

  class DbService(backend: jdbc.MySQLProfile.backend.Database) {
    def hoge: Unit = Unit

    def terminate(): Unit = {
      backend.close()
    }
  }

}
