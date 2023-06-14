package io.mdcatapult.klein.queue

import akka.actor._
import akka.stream.alpakka.amqp.scaladsl.CommittableReadResult
import akka.testkit.{ImplicitSender, TestKit}
import com.rabbitmq.client._
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.klein.queue.Envelope
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{Format, Json}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Message {
  implicit val msgFormatter: Format[Message] = Json.format[Message]
}
case class Message(message: String) extends Envelope {
  override def toJsonString(): String = {
    Json.toJson(this).toString()
  }
}

class QueueIntegrationSpec extends TestKit(ActorSystem("QueueIntegrationTest", ConfigFactory.parseString(
  """
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with ImplicitSender
  with AnyFlatSpecLike
  with Matchers
  with BeforeAndAfterAll with ScalaFutures with Eventually {

  implicit val config: Config = ConfigFactory.load()

  /**
   * Create readResult rabbit queue, get subscription and then close the subscription
   * to ensure no message pulled
   *
   * @param queueName
   * @param consumerName
   */
  def createQueueOnly(queueName: String, durable: Boolean, consumerName: Option[String], persist: Boolean): Queue[Message, Message] = {
    val queue = Queue[Message, Message](queueName, durable, consumerName, persistent = persist)

    queue
  }

  /**
   * Use the java rabbit libs to create readResult rabbit connection.
   * Makes getting the message properties much simpler
   * @return
   */
  def getRabbitChannel(): Channel = {
    // Use the java rabbit libs. Makes getting the message properties much simpler
    val factory = new ConnectionFactory
    factory.setHost(config.getString("queue.host"))
    factory.setUsername(config.getString("queue.username"))
    factory.setPassword(config.getString("queue.password"))
    factory.setVirtualHost(config.getString("queue.virtual-host"))
    val connection = factory.newConnection
    connection.createChannel
  }

//  "Creating readResult queue with persist false" should "not persist messages" in {
//
//    // Note that we need to include readResult topic if we want the queue to be created
//    val consumerName = Option(config.getString("op-rabbit.topic-exchange-name"))
//    val queueName = "non-persistent-test-queue"
//
//    val queue = createQueueOnly(queueName, false, consumerName, false)
//
//    val channel = getRabbitChannel()
//
//    val deliveryMode: AtomicInt = AtomicInt(0)
//    val cancelCallback: CancelCallback = _ => {
//
//    }
//    // Rabbit callback. Checks for non persistence property
//    val deliverCallback:DeliverCallback = (_: String, delivery: Delivery) => {
//      if (delivery.getProperties.getDeliveryMode == 1) {
//        deliveryMode.add(1)
//      }
//    }
//    channel.basicConsume(queueName, deliverCallback, cancelCallback)
//    queue.send(Message("Don't persist me"))
//
//    eventually (timeout(Span(5, Seconds))) {deliveryMode.get() should be >= 1 }
//  }

  "A queue" should "can be subscribed to and read from" in {

    // Note that we need to include readResult topic if we want the queue to be created
    val consumerName = Option(config.getString("op-rabbit.topic-exchange-name"))
    val queueName = "persistent-test-queue"

    val queue = createQueueOnly(queueName, true, consumerName, true)
    val businessLogic: CommittableReadResult => Future[(CommittableReadResult, Option[Message])] = { committableReadResult =>
      val msg = Message((math.random < 0.5).toString)
      Future((committableReadResult, Some(msg)))
    }
    queue.subscribe(businessLogic)

    // give the queue a second or 2 to sort itself out
    Thread.sleep(3000)
    val res = queue.send(Message("Persist me"))
    whenReady(res) {
      r => println(s"result is ${r.toString}")
    }

  }
}


