package io.mdcatapult.klein.queue

import akka.actor._
import com.spingo.op_rabbit.PlayJsonSupport._
import com.spingo.op_rabbit.properties.{DeliveryModePersistence, MessageProperty}
import com.spingo.op_rabbit.{Queue => RQueue, RecoveryStrategy => OpRecoveryStrategy, _}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Format

import scala.concurrent.Future

object Queue {

  /**
   * Ensure that the properties contains a DeliveryModePersistence(true). Remove any DeliveryModePersistence(false)
   *
   * @param properties
   * @return
   */
  def ensureDeliveryModePersistence(properties: Seq[MessageProperty]): Seq[MessageProperty] = {
    (properties.exists(property => property match {
      case prop: DeliveryModePersistence => prop.persistent
      case _ => false
    }) match {
      case true => properties
      case false => properties :+ DeliveryModePersistence(true)
    }).filterNot(_ == DeliveryModePersistence(false))
  }
}

/**
  * Queue Abstraction
  */
case class Queue[T <: Envelope](name: String, consumerName: Option[String] = None, topics: Option[String] = None)
                               (implicit actorSystem: ActorSystem, config: Config, formatter: Format[T])
  extends Subscribable with Sendable[T] with LazyLogging {

  import actorSystem.dispatcher

  val rabbit: ActorRef = actorSystem.actorOf(
    Props(classOf[Rabbit], ConnectionParams.fromConfig(config.getConfig("op-rabbit.connection"))),
    name
  )

  implicit val recoveryStrategy: OpRecoveryStrategy = RecoveryStrategy.errorQueue(errorQueueName = "errors", sendErrors = config.getBoolean("error.queue"), consumerName = consumerName)

  /**
    * subscribe to queue/topic and execute callback on receipt of message
    *
    * @param callback Function
    * @return SubscriptionRef
    */
  def subscribe(callback: (T, String) => Any, concurrent: Int = 1): SubscriptionRef = Subscription.run(rabbit) {
    import Directives._
    channel(qos = concurrent) {
      consume(RQueue.passive(topic(queue(name), List(topics.getOrElse(name))))) {
        (body(as[T]) & exchange) {
          (msg, ex) =>
            callback(msg, ex) match {
              // Success
              case f: Future[Any] => ack(f)
              // Possible failure. Really should not happen
              case _ => {
                logger.error(s"Message appears to have completed without returning value. Investigate logs of consumer handling queue $name for possible reason.")
                // Delete message from queue and flag as failed
                nack()
              }
            }
        }
      }
    }
  }

  /**
    * Send message directly to configured queue. Method ensures that properties always
    * contains DeliveryModePersistence(true)
    *
    * @param envelope message to send
    */
  def send(envelope: T, properties: Seq[MessageProperty] = Seq[MessageProperty](DeliveryModePersistence(true))): Unit =
    rabbit ! Message.queue(envelope, name, Queue.ensureDeliveryModePersistence(properties))

}
