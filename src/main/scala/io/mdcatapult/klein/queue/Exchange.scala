package io.mdcatapult.klein.queue

import akka.actor._
import com.spingo.op_rabbit.PlayJsonSupport._
import com.spingo.op_rabbit.properties.MessageProperty
import com.spingo.op_rabbit.{RabbitControl, Queue ⇒ RQueue, _}
import play.api.libs.json.{Format, Reads, Writes}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * Queue Abstraction
  */
case class Exchange[T <: Envelope](name: String, routingKey: Option[String] = None)(
  implicit actorSystem: ActorSystem, ex: ExecutionContext, reader: Reads[T], writer: Writes[T], formatter: Format[T]
) extends Sendable[T]{

  val rabbit: ActorRef = actorSystem.actorOf(Props[RabbitControl])

  /**
    * Send message directly to configured exchange
    *
    * @param envelope message to send
    */
  def send(envelope: T, properties: Seq[MessageProperty] = Seq.empty): Unit =
    rabbit ! Message.exchange(
      envelope,
      name,
      if (routingKey.isDefined) routingKey.get else "",
      properties)

}
