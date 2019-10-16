package io.mdcatapult.klein.queue

import akka.actor.ActorRef
import com.spingo.op_rabbit.{RecoveryStrategy => OpRecoveryStrategy}

trait Subscribable {
  val name: String
  val rabbit: ActorRef
  implicit val recoveryStrategy: OpRecoveryStrategy
}
