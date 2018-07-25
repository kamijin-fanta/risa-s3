package com.github.kamijin_fanta.common

import akka.actor.ActorSystem

trait ActorSystemServiceComponent {
  implicit def actorSystem: ActorSystem
}
