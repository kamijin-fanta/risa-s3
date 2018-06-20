package com.github.kamijin_fanta.aws4

import scala.concurrent.Future

trait AccountProvider {
  def findAccessKey(accessKey: String): Future[Option[AccessCredential]]
}
