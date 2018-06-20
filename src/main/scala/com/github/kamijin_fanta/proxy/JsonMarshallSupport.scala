package com.github.kamijin_fanta.proxy

import akka.http.scaladsl.marshalling.{ Marshaller, ToEntityMarshaller }
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import org.json4s.native.JsonMethods
import org.json4s.{ DefaultFormats, Extraction, Formats }

trait JsonMarshallSupport {
  private implicit val fmt: Formats = DefaultFormats

  def writeJson[T <: AnyRef](item: T): String = {
    JsonMethods.pretty(JsonMethods.render(Extraction.decompose(item)))
  }

  def formats[T <: AnyRef](): ToEntityMarshaller[T] = {
    Marshaller.opaque { item =>
      HttpEntity(ContentTypes.`application/json`, writeJson(item))
    }
  }

  implicit val errorResponseFormats = formats[ErrorResponse]
}
