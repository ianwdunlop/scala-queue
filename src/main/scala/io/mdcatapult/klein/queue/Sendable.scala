package io.mdcatapult.klein.queue

import akka.actor.ActorRef
import com.spingo.op_rabbit.properties.MessageProperty

trait Sendable[T <: Envelope] {
  val rabbit: ActorRef

  def send(envelope: T, properties: Seq[MessageProperty] = Seq.empty): Unit
}
