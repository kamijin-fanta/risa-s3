package com.github.kamijin_fanta.response

import java.time.{ OffsetDateTime, ZoneOffset, ZonedDateTime }

import scala.xml.Elem

trait XmlSerializable {
  def asXml: Elem
}

case class Content(key: String, lastModified: OffsetDateTime, md5: String, size: Long, storageClass: String)
case class ListBucketResult(bucket: String, prefix: Option[String], delimiter: Option[String], commonPrefixes: List[String], contents: List[Content], isTruncated: Boolean) extends XmlSerializable {
  implicit class RichOffsetDateTime(dateTime: OffsetDateTime) {
    def toUtc: ZonedDateTime =
      dateTime.atZoneSameInstant(ZoneOffset.UTC)
  }
  def asXml: Elem =
    <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
      <Name>{ bucket }</Name>
      { prefix.map(p => <Prefix>{ p }</Prefix>) }
      { delimiter.map(d => <Delimiter>{ d }</Delimiter>) }
      { if (commonPrefixes.nonEmpty) <CommonPrefixes> { commonPrefixes.map(cp => <Prefix>{ cp }</Prefix>) } </CommonPrefixes> }
      <KeyCount>{ contents.length }</KeyCount>
      <MaxKeys>1000</MaxKeys>
      <IsTruncated>{ isTruncated }</IsTruncated>
      {
        contents.map(content =>
          <Contents>
            <Key>{ content.key }</Key>
            <LastModified>{ content.lastModified.toUtc.toString }</LastModified>
            <ETag>{ content.md5 }</ETag>
            <Size>{ content.size }</Size>
            <StorageClass>{ content.storageClass }</StorageClass>
          </Contents>)
      }
    </ListBucketResult>
}
