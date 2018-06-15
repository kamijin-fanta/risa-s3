package com.github.kamijin_fanta.storageProvider

import com.github.kamijin_fanta.response.{ ListAllMyBucketsResult, ListBucketResult }

import scala.concurrent.Future

trait StorageProvider {

  /*
    todo
      - createBucket
      - deleteBucket
      - pubObject
      - getObject
      - deleteObject
      - copyObject
      - multiPart
   */
  def listBuckets(requestMeta: RequestMeta): Future[ListAllMyBucketsResult]

  def listBucket(requestMeta: RequestMeta, bucket: String, prefix: Option[String], delimiter: Option[String], maxkeys: Option[Int]): Future[ListBucketResult]

}
