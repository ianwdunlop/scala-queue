package io.mdcatapult.klein.queue

import akka.actor._
import com.spingo.op_rabbit.PlayJsonSupport._
import com.spingo.op_rabbit.properties.MessageProperty
import com.spingo.op_rabbit.{RabbitControl, _}
import play.api.libs.json.Format

/**
  * Queue Abstraction
  */
case class Topic[T <: Envelope](name: String, exchange: Option[String] = None)(
  implicit actorSystem: ActorSystem, formatter: Format[T]
) extends Sendable[T] {

  val rabbit: ActorRef = actorSystem.actorOf(Props[RabbitControl])

  /**
    * Send message directly to configured exchange
    *
    * @param envelope message to send
    */
  def send(envelope: T, properties: Seq[MessageProperty] = Seq.empty): Unit =
    rabbit ! Message.topic(
      envelope,
      name,
      exchange.getOrElse(RabbitControl.topicExchangeName),
      properties
    )

}