package io.mdcatapult.klein.queue

import akka.actor._
import com.spingo.op_rabbit.PlayJsonSupport._
import com.spingo.op_rabbit.{RabbitControl, Queue ⇒ RQueue, _}
import play.api.libs.json.{Format, Reads, Writes}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * Queue Abstraction
  */
class Queue[T <: Envelope](queueName: String, topics: Option[String] = None)(
  implicit actorSystem: ActorSystem, ex: ExecutionContext, reader: Reads[T], writer: Writes[T], formatter: Format[T]
) {

  val rabbit: ActorRef = actorSystem.actorOf(Props[RabbitControl])
  //  implicit val recoveryStrategy: RecoveryStrategy = RecoveryStrategy.none
  implicit private val recoveryStrategy: RecoveryStrategy = RecoveryStrategy.abandonedQueue()

  /**
    * subscribe to queue/topic and execute callback on reciept of message
    *
    * @param callback Function
    * @return SubscriptionRef
    */
  def subscribe(callback: (T, String) ⇒ Any, concurrent: Int = 1): SubscriptionRef = Subscription.run(rabbit) {
    import Directives._
    channel(qos = concurrent) {
      consume(RQueue.passive(topic(queue(queueName), List(topics.getOrElse(queueName))))) {
        (body(as[T]) & routingKey) {
          (msg, key) ⇒
            callback(msg, key) match {
              case f: Future[Any] ⇒ ack(f)
              case _ ⇒ ack
            }
        }
      }
    }
  }

  /**
    * Send message directly to configured queue
    *
    * @param envelope message to send
    */
  def send(envelope: T): Unit = rabbit ! Message.queue(envelope, queue = queueName)

}
