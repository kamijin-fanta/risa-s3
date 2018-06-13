package aws4

case class Credential(key: String, date: String, region: String, service: String, signing: String) {
  val scope = s"$date/$region/$service/$signing"
}

case class AwsAuthorizationHeader(algorithm: String, credential: Credential, signedHeaders: List[String], signature: String)
