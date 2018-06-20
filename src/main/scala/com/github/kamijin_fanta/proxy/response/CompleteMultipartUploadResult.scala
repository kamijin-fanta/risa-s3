package com.github.kamijin_fanta.response

import scala.xml.Elem

case class CompleteMultipartUploadResult(location: String, bucket: String, key: String, etag: String) extends XmlSerializable {
  def asXml: Elem =
    <CompleteMultipartUploadResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
      <Location>http://Example-Bucket.s3.amazonaws.com/Example-Object</Location>
      <Bucket>Example-Bucket</Bucket>
      <Key>Example-Object</Key>
      <ETag>"3858f62230ac3c915f300c664312c11f-9"</ETag>
    </CompleteMultipartUploadResult>
}
