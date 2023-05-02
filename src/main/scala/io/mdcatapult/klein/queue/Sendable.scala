package io.mdcatapult.klein.queue

// import akka.actor.ActorRef
// import com.rabbitmq.client.AMQP

trait Sendable[T <: Envelope] {
  val name: String
  // val rabbit: ActorRef
  // def send(envelope: T, properties: Option[AMQP.BasicProperties] = None): Unit
}
