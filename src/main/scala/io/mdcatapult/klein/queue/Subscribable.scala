package io.mdcatapult.klein.queue

import akka.actor.ActorRef
import com.spingo.op_rabbit.RecoveryStrategy

trait Subscribable {
  val rabbit: ActorRef
  implicit val recoveryStrategy: RecoveryStrategy
}
