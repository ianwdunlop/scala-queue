package io.mdcatapult.klein.queue

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import com.rabbitmq.client.{CancelCallback, Delivery}
import monix.execution.atomic.AtomicInt
import org.scalatest.time.{Seconds, Span}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{Format, Json}

import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback

object Message {
  implicit val msgFormatter: Format[Message] = Json.format[Message]
}
case class Message(message: String) extends Envelope

class QueueIntegrationSpec extends TestKit(ActorSystem("QueueIntegrationTest", ConfigFactory.parseString(
  """
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with ImplicitSender
  with AnyFlatSpecLike
  with Matchers
  with BeforeAndAfterAll with ScalaFutures with Eventually {

  "A queue" should
    "be created if it does not exist" in {

    implicit val config: Config = ConfigFactory.load()

    // Note that we need to include a topic if we want the queue to be created
    val consumerName = Option(config.getString("op-rabbit.topic-exchange-name"))
    val queueName = "non-persistent-test-queue"

    val queue = Queue[Message](queueName, consumerName, persistent = false)

    // Use the java rabbit libs. Makes getting the message properties much simpler
    val factory = new ConnectionFactory
    factory.setHost(config.getStringList("op-rabbit.connection.hosts").get(0))
    factory.setUsername(config.getString("op-rabbit.connection.username"))
    factory.setPassword(config.getString("op-rabbit.connection.password"))
    factory.setVirtualHost(config.getString("op-rabbit.connection.virtual-host"))
    val connection = factory.newConnection
    val channel = connection.createChannel

    val deliveryMode: AtomicInt = AtomicInt(0)
    val cancelCallback: CancelCallback = (consumerTag) => {

    }
    val deliverCallback:DeliverCallback = (consumerTag: String, delivery: Delivery) => {
      if (delivery.getProperties.getDeliveryMode == 1) {
        deliveryMode.add(1)
      }
    }
    channel.basicConsume(queueName, deliverCallback, cancelCallback)
    queue.send(Message("Don't persist me"))

    eventually (timeout(Span(5, Seconds))) {deliveryMode.get() should be >= 1 }
  }

}


