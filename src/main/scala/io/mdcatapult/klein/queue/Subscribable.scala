package io.mdcatapult.klein.queue

trait Subscribable {
  val name: String
  // val rabbit: ActorRef
  // implicit val recoveryStrategy: OpRecoveryStrategy
}
