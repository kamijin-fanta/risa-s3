package com.github.kamijin_fanta.data.metaProvider

import Tables._
import com.github.kamijin_fanta.ApplicationConfig
import com.github.kamijin_fanta.common.model.Tablet
import com.github.kamijin_fanta.common.{ DbService, DbServiceComponent }
import com.github.kamijin_fanta.data.ClusterManagementServiceComponent

import scala.concurrent.{ ExecutionContext, Future }

trait MetaBackendServiceComponent {
  self: DbServiceComponent with ClusterManagementServiceComponent =>
  def metaBackendService: MetaBackendService

  class MetaBackendService(dbService: DbService)(implicit ctx: ExecutionContext) {

    import slick.jdbc.MySQLProfile.api._

    ////////////////// node //////////////////
    def nodes(nodeGroup: Int): Future[Seq[VolumeNodeRow]] = {
      dbService.backend.run(
        VolumeNode.filter(_.volumeGroup === nodeGroup).result)
    }

    def otherNodes(nodeGroup: Int, selfNodeId: Int): Future[Seq[VolumeNodeRow]] = {
      nodes(nodeGroup).map(_.filterNot(_.id == selfNodeId))
    }

    def otherNodes()(implicit applicationConfig: ApplicationConfig): Future[Seq[VolumeNodeRow]] = {
      clusterManagementService.currentClusterSetting() match {
        case Some(settings) =>
          otherNodes(applicationConfig.data.group, settings.volumeNodeRow.id)
        case None =>
          Future.successful(Seq())
      }
    }

    def node(group: Int, node: Int): Future[Option[VolumeNodeRow]] = {
      dbService.backend.run(
        VolumeNode
          .filter(c => c.volumeGroup === group && c.id === node)
          .result
          .headOption)
    }
    def selfNode()(implicit applicationConfig: ApplicationConfig): Future[Option[VolumeNodeRow]] = {
      clusterManagementService.currentClusterSetting() match {
        case Some(settings) =>
          dbService.backend.run(
            VolumeNode
              .filter(c => c.volumeGroup === applicationConfig.data.group && c.id === settings.volumeNodeRow.id)
              .result.headOption)
        case None =>
          Future.successful(None)
      }
    }
    def updateNode(nodeRow: VolumeNodeRow): Future[Int] = {
      dbService.backend.run(
        VolumeNode
          .filter(_.id === nodeRow.id)
          .update(nodeRow))
    }
    def newNode(nodeRow: VolumeNodeRow) = {
      dbService.backend.run {
        (VolumeNode returning VolumeNode.map(_.id)) += nodeRow
      }
    }

    ////////////////// volumeGroup //////////////////
    def selfGroup(group: Int): Future[Option[VolumeGroupRow]] = {
      dbService.backend.run(
        VolumeGroup.filter(_.id === group).result.headOption)
    }
    def selfGroup()(implicit applicationConfig: ApplicationConfig): Future[Option[VolumeGroupRow]] = {
      selfGroup(applicationConfig.data.group)
    }

    ////////////////// VolumeFile //////////////////
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
