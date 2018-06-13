package aws4

import scala.util.parsing.combinator.RegexParsers

object AwsAuthorizationHeaderParser extends RegexParsers {
  private val algorithm = """[\w-]+""".r

  private val value = """[^\s,]+""".r
  private val keyValue = ("""\w+""".r <~ "=") ~ value

  private def repNsep[T](num: Int, p: => Parser[T], q: => Parser[Any]): Parser[List[T]] =
    p ~ repN(num - 1, q ~> p) ^^ { case x ~ y => x :: y }

  private val credential = "Credential=" ~> repNsep(5, """[\w-]+""".r, "/") ^^ { res =>
    Credential(res(0), res(1), res(2), res(3), res(4))
  }

  case class SignedHeaders(signedHeaders: List[String])
  private val signedHeaders = "SignedHeaders=" ~> repsep("""[\w-]+""".r, ";") ^^ { res => SignedHeaders(res) }
  private val signature = "Signature=" ~> """[\w-]+""".r

  private val fields = credential | signedHeaders | signature

  def headerValue: Parser[AwsAuthorizationHeader] = algorithm ~ repsep(fields, ",") ^^ { // res =>
    case alg ~ params =>
      AwsAuthorizationHeader(
        alg,
        params.collect { case x: Credential => x }.head,
        params.collect { case x: SignedHeaders => x.signedHeaders }.head,
        params.collect { case x: String => x }.head)
  }

  def apply(input: String): Either[String, AwsAuthorizationHeader] = parseAll(headerValue, input) match {
    case Success(header, next) => Right(header)
    case NoSuccess(errorMessage, next) => Left(s"$errorMessage on line ${next.pos.line} on column ${next.pos.column}")
  }
}
