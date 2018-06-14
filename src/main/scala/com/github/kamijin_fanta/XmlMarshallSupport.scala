package com.github.kamijin_fanta

import akka.http.scaladsl.marshalling.{ Marshaller, ToEntityMarshaller }
import akka.http.scaladsl.model._
import com.github.kamijin_fanta.response.XmlSerializable

trait XmlMarshallSupport {

  def xmlFormats(): ToEntityMarshaller[XmlSerializable] = {
    Marshaller.opaque { item =>
      HttpEntity(
        ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`),
        item.asXml.toString())
    }
  }

  implicit val xmlResponseFormats: ToEntityMarshaller[XmlSerializable] = xmlFormats
}
