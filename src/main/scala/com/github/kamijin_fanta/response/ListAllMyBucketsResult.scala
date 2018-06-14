package com.github.kamijin_fanta.response

import java.time.OffsetDateTime

import scala.xml.Elem

case class Bucket(name: String, creationDate: OffsetDateTime)

case class ListAllMyBucketsResult(ownerName: String, ownerUUID: String, buckets: List[Bucket]) extends XmlSerializable {
  def asXml: Elem =
    <ListAllMyBucketsResult xmlns="http://s3.amazonaws.com/doc/2006-03-01">
      <Owner>
        <ID>{ ownerUUID }</ID>
        <DisplayName>{ ownerName }</DisplayName>
      </Owner>
      <Buckets>
      {
        buckets.map(bucket =>
          <Bucket>
            <Name>{ bucket.name }</Name>
            <CreationDate>{ bucket.creationDate.toString }</CreationDate>
          </Bucket>)
      }
      </Buckets>
    </ListAllMyBucketsResult>
}