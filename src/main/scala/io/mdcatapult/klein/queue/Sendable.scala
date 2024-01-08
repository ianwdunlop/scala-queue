package io.mdcatapult.klein.queue

 import akka.Done
 import com.rabbitmq.client.AMQP
 import com.rabbitmq.client.AMQP.BasicProperties

 import scala.concurrent.Future

trait Sendable[T <: Envelope] {
  val name: String
  val persistent: Boolean

  /**
   * Send a message of type T to the the queue
   * @param envelope
   * @param properties
   * @return
   */
   def send(envelope: T, properties: Option[AMQP.BasicProperties] = None): Future[Done]

  /** Check if messages are to be persisted and add delivery mode property
   *
   * @param properties
   * @return
   */
  def persistMessages(properties: Option[AMQP.BasicProperties]): BasicProperties = {
    val basicProperties = properties match {
      case None => new BasicProperties.Builder().build()
      case Some(props) => props
    }
    val persistedProps = if (persistent) {
      basicProperties.builder().deliveryMode(2).build()
    } else {
      basicProperties.builder().deliveryMode(1).build()
    }
    persistedProps
  }
}
