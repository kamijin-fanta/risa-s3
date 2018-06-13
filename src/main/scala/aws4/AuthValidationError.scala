package aws4

sealed trait AuthValidationError extends Throwable

case class NotFoundHeader(key: String) extends Throwable(s"Not found header $key") with AuthValidationError
case class FailedParseHeader(key: String) extends Throwable(s"Failed parse header $key") with AuthValidationError
case class AuthError() extends Throwable(s"auth error") with AuthValidationError
