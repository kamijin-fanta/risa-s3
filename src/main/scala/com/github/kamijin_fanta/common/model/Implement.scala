package com.github.kamijin_fanta.common.model

import Tables._
import akka.http.scaladsl.model.Uri

object Implement {

  implicit class RichVolumeNodeRow(self: VolumeNodeRow) {
    val apiBaseUri = Uri(self.address)
  }

}
