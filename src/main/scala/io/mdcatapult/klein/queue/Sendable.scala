package io.mdcatapult.klein.queue

// import akka.actor.ActorRef
 import akka.Done
 import com.rabbitmq.client.AMQP

 import scala.concurrent.Future

trait Sendable[T <: Envelope] {
  val name: String
  val persistent: Boolean
  // val rabbit: ActorRef
   def send(envelope: T, properties: Option[AMQP.BasicProperties] = None): Future[Done]

  /**
   * Check if messages are to be persisted and add delivery mode property
   *
   * @param properties
   * @return
   */
  def persistMessages(properties: Option[AMQP.BasicProperties]) = {
    // At the moment the properties have to be present
    val persistedProps = if (persistent) {
      properties.get.builder().deliveryMode(2).build()
    } else {
      properties.get.builder().deliveryMode(1).build()
    }
    persistedProps
  }
}
