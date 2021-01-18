package io.mdcatapult.klein.queue

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import com.rabbitmq.client.{CancelCallback, Channel, ConnectionFactory, DeliverCallback, Delivery}
import monix.execution.atomic.AtomicInt
import org.scalatest.time.{Seconds, Span}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{Format, Json}
import com.spingo.op_rabbit.SubscriptionRef

import scala.concurrent.duration._
import scala.concurrent.Await

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

  implicit val config: Config = ConfigFactory.load()

  /**
   * Create a rabbit queue, get subscription and then close the subscription
   * to ensure no message pulled
   *
   * @param queueName
   * @param consumerName
   */
  def createQueueOnly(queueName: String, consumerName: Option[String], persist: Boolean, exchangeName: Option[String] = None): Queue[Message] = {
    val queue = Queue[Message](queueName, consumerName, persistent = persist, exchange = exchangeName)
    val subscription: SubscriptionRef = queue.subscribe((msg: Message, key: String) => {})
    Await.result(subscription.initialized, 5.seconds)
    subscription.close()
    queue
  }

  /**
   * Use the java rabbit libs to create a rabbit connection.
   * Makes getting the message properties much simpler
   * @return
   */
  def getRabbitChannel(): Channel = {
    // Use the java rabbit libs. Makes getting the message properties much simpler
    val factory = new ConnectionFactory
    factory.setHost(config.getStringList("op-rabbit.connection.hosts").get(0))
    factory.setUsername(config.getString("op-rabbit.connection.username"))
    factory.setPassword(config.getString("op-rabbit.connection.password"))
    factory.setVirtualHost(config.getString("op-rabbit.connection.virtual-host"))
    val connection = factory.newConnection
    connection.createChannel
  }

  "Creating a queue with persist false" should "not persist messages" in {

    // Note that we need to include a topic if we want the queue to be created
    val consumerName = Option(config.getString("op-rabbit.topic-exchange-name"))
    val queueName = "non-persistent-test-queue"

    val queue = createQueueOnly(queueName, consumerName, false)

    val channel = getRabbitChannel()

    val deliveryMode: AtomicInt = AtomicInt(0)
    val cancelCallback: CancelCallback = _ => {

    }
    // Rabbit callback. Checks for non persistence property
    val deliverCallback:DeliverCallback = (_: String, delivery: Delivery) => {
      if (delivery.getProperties.getDeliveryMode == 1) {
        deliveryMode.add(1)
      }
    }
    channel.basicConsume(queueName, deliverCallback, cancelCallback)
    queue.send(Message("Don't persist me"))

    eventually (timeout(Span(5, Seconds))) {deliveryMode.get() should be >= 1 }
  }

  "A queue" should "persists messages by default" in {

    // Note that we need to include a topic if we want the queue to be created
    val consumerName = Option(config.getString("op-rabbit.topic-exchange-name"))
    val queueName = "persistent-test-queue"

    val queue = createQueueOnly(queueName, consumerName, true)

    val channel = getRabbitChannel()

    val deliveryMode: AtomicInt = AtomicInt(0)
    val cancelCallback: CancelCallback = _ => {

    }
    // Rabbit callback. Checks for persistence property (the op-rabbit default and enforced by default in Queue)
    val deliverCallback:DeliverCallback = (_: String, delivery: Delivery) => {
      if (delivery.getProperties.getDeliveryMode == 2) {
        deliveryMode.add(1)
      }
    }
    channel.basicConsume(queueName, deliverCallback, cancelCallback)
    queue.send(Message("Persist me"))

    eventually (timeout(Span(5, Seconds))) {deliveryMode.get() should be >= 1 }
  }

  "An exchange" should "be created" in {
    val queueName = "test-queue"
    val testExchange = "test-exchange"
    val queue = createQueueOnly(queueName, Some("test-consumer"), persist = false, exchangeName = Some(testExchange))
    val channel = getRabbitChannel()

    var foundExchange: String = ""
    val cancelCallback: CancelCallback = _ => {}
    // Rabbit callback. Checks for the exchange
    val deliverCallback:DeliverCallback = (_: String, delivery: Delivery) => {
      foundExchange = delivery.getEnvelope.getExchange
    }
    channel.basicConsume(queueName, deliverCallback, cancelCallback)
    queue.send(Message("test message"))
    eventually (timeout(Span(5, Seconds))) {foundExchange should equal(testExchange)}
  }

}


