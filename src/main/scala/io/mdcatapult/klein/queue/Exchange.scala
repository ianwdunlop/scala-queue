// package io.mdcatapult.klein.queue

// import akka.actor._
// import com.spingo.op_rabbit.{RabbitControl, _}
// import com.rabbitmq.client.AMQP

// /** Queue Abstraction
//   */
// case class Exchange[T <: Envelope](
//     name: String,
//     routingKey: Option[String] = None
// )(implicit actorSystem: ActorSystem)
//     extends Sendable[T] {

//   val rabbit: ActorRef = actorSystem.actorOf(Props[RabbitControl])

//   /** Send message directly to configured exchange
//     *
//     * @param envelope
//     *   message to send
//     */
//   def send(envelope: T, properties: Option[AMQP.BasicProperties] = None): Unit =
//     rabbit ! Message.exchange(
//       envelope,
//       name,
//       routingKey.getOrElse(""),
//       properties
//     )

// }
