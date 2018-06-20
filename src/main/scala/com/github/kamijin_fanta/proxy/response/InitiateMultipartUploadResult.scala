package com.github.kamijin_fanta.response

import scala.xml.Elem

case class InitiateMultipartUploadResult(bucket: String, key: String, uploadId: String) extends XmlSerializable {
  def asXml: Elem =
    <InitiateMultipartUploadResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
      <Bucket>{ bucket }</Bucket>
      <Key>{ key }</Key>
      <UploadId>{ uploadId }</UploadId>
    </InitiateMultipartUploadResult>
}
