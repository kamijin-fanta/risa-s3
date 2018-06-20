package com.github.kamijin_fanta.response

import scala.xml.Elem

trait XmlSerializable {
  def asXml: Elem
}

