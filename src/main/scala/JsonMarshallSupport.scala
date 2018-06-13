import akka.http.scaladsl.marshalling.{ Marshaller, ToEntityMarshaller }
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.write

trait JsonMarshallSupport {
  def writeJson[T <: AnyRef](item: T): String = write(item)(DefaultFormats)

  def formats[T <: AnyRef](): ToEntityMarshaller[T] = {
    Marshaller.opaque { item =>
      HttpEntity(ContentTypes.`application/json`, writeJson(item))
    }
  }

  implicit val errorResponseFormats = formats[ErrorResponse]
}
