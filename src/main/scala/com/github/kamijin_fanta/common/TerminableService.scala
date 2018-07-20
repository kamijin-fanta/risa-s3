package com.github.kamijin_fanta.common

import scala.concurrent.{ExecutionContextExecutor, Future}

trait TerminableService {

  def run()(implicit ctx: ExecutionContextExecutor): Future[Unit]

  def terminate()(implicit ctx: ExecutionContextExecutor): Future[Unit]
}
