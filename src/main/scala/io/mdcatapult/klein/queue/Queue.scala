package io.mdcatapult.klein.queue

import akka.actor._
import com.spingo.op_rabbit.PlayJsonSupport._
import com.spingo.op_rabbit.properties.MessageProperty
import com.spingo.op_rabbit.{RabbitControl, Queue => RQueue, _}
import com.typesafe.config.Config
import play.api.libs.json.{Format, Reads, Writes}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.postfixOps

/**
  * Queue Abstraction
  */
case class Queue[T <: Envelope](name: String, consumerName: Option[String] = None, topics: Option[String] = None)
                               (implicit actorSystem: ActorSystem, config: Config, reader: Reads[T], writer: Writes[T], formatter: Format[T])
  extends Subscribable with Sendable[T] {

  implicit val ex: ExecutionContextExecutor = actorSystem.dispatcher
  val rabbit: ActorRef = actorSystem.actorOf(
    Props(classOf[RabbitControl], ConnectionParams.fromConfig(config.getConfig("op-rabbit.connection"))),
    name
  )
  implicit val recoveryStrategy: RecoveryStrategy = RecoveryStrategy.errorQueue("errors", consumerName)

  /**
    * subscribe to queue/topic and execute callback on reciept of message
    *
    * @param callback Function
    * @return SubscriptionRef
    */
  def subscribe(callback: (T, String) ⇒ Any, concurrent: Int = 1): SubscriptionRef = Subscription.run(rabbit) {
    import Directives._
    channel(qos = concurrent) {
      consume(RQueue.passive(topic(queue(name), List(topics.getOrElse(name))))) {
        (body(as[T]) & exchange) {
          (msg, ex) ⇒
            callback(msg, ex) match {
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
  def send(envelope: T, properties: Seq[MessageProperty] = Seq.empty): Unit =
    rabbit ! Message.queue(envelope, name, properties)

}