package com.github.kamijin_fanta.data.metaProvider

import Tables._
import com.github.kamijin_fanta.ApplicationConfig
import com.github.kamijin_fanta.common.model.Tablet
import com.github.kamijin_fanta.common.{ DbService, DbServiceComponent }

import scala.concurrent.{ ExecutionContext, Future }

trait MetaBackendServiceComponent extends DbServiceComponent {
  def metaBackendService: MetaBackendService

  class MetaBackendService(dbService: DbService)(implicit ctx: ExecutionContext) {

    import slick.jdbc.MySQLProfile.api._

    def nodes(nodeGroup: Int): Future[Seq[VolumeNodeRow]] = {
      dbService.backend.run(
        VolumeNode.filter(_.volumeGroup === nodeGroup).result)
    }

    def otherNodes(nodeGroup: Int, selfNodeId: Int): Future[Seq[VolumeNodeRow]] = {
      nodes(nodeGroup).map(_.filterNot(_.id == selfNodeId))
    }

    def otherNodes()(implicit applicationConfig: ApplicationConfig): Future[Seq[VolumeNodeRow]] = {
      otherNodes(applicationConfig.data.group, applicationConfig.data.node)
    }

    def selfNode()(implicit applicationConfig: ApplicationConfig): Future[Option[VolumeNodeRow]] = {
      dbService.backend.run(
        VolumeNode.filter(_.id === applicationConfig.data.node).result.headOption)
    }

    def addItem(volumeGroup: Int, tablet: String, name: String, hash: String): Future[Int] = {
      val newData = VolumeFileRow(volumeGroup, tablet, name, hash)
      dbService.backend.run(VolumeFile += newData)
    }

    def tabletItems(volumeGroup: Int, tablet: String) = {
      dbService.backend.run(
        VolumeFile
          .filter(r => r.volumeGroup === volumeGroup && r.tablet === tablet)
          .result)
    }
    def hasTablets(volumeGroup: Int): Future[Seq[Tablet]] = {
      dbService.backend.run(
        VolumeFile
          .distinctOn(c => (c.volumeGroup, c.tablet))
          .result).map(_.map(t => Tablet(t.volumeGroup, t.tablet)))
    }
  }
}
