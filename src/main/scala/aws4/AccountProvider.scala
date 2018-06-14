package aws4

import scala.concurrent.Future

trait AccountProvider {
  def findAccessKey(accessKey: String): Future[Option[AccessCredential]]
}
