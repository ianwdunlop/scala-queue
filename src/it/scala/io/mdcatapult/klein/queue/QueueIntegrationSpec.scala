package io.mdcatapult.klein.queue

import akka.actor._
import akka.stream.alpakka.amqp.scaladsl.CommittableReadResult
import akka.testkit.{ImplicitSender, TestKit}
import com.rabbitmq.client._
import com.typesafe.config.{Config, ConfigFactory}
import io.mdcatapult.klein.queue.Envelope
//import monix.execution.atomic.AtomicInt
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
    "\"message\": \"" + message + "\""
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
   * Create a rabbit queue, get subscription and then close the subscription
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
   * Use the java rabbit libs to create a rabbit connection.
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

//  "Creating a queue with persist false" should "not persist messages" in {
//
//    // Note that we need to include a topic if we want the queue to be created
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

  "A queue" should "persist messages by default" in {

    // Note that we need to include a topic if we want the queue to be created
    val consumerName = Option(config.getString("op-rabbit.topic-exchange-name"))
    val queueName = "persistent-test-queue"

    val queue = createQueueOnly(queueName, true, consumerName, true)
    val businessLogic: CommittableReadResult => Future[(CommittableReadResult, Option[Message])] = { committableReadResult =>

      //      implicit val format = DefaultFormats // implicit format is needed to read JSON in to the person case class
      val msg = Message((math.random < 0.5).toString)
      Future((committableReadResult, Some(msg)))
      //    }

      //    eventually (timeout(Span(5, Seconds))) {deliveryMode.get() should be >= 1 }
    }
    val a = queue.subscribe(businessLogic)
    Thread.sleep(3000)
    a.map(f => f.map(r => println(s"dt: ${r.envelope.getDeliveryTag}")))
    val res = queue.send(Message("Persist me"))
//    res.map(d => println(d.toString))
    whenReady(res) {
      r => println(s"result is ${r.toString}")
    }

//    val channel = getRabbitChannel()
//
//    val deliveryMode: AtomicInt = AtomicInt(0)
//    val cancelCallback: CancelCallback = _ => {
//
//    }
//    // Rabbit callback. Checks for persistence property
//    val deliverCallback: DeliverCallback = (_: String, delivery: Delivery) => {
//      println("Delivery!")
//      if (delivery.getProperties.getDeliveryMode == 2) {
//        deliveryMode.add(1)
//      }
//    }
//    channel.basicConsume(queueName, deliverCallback, cancelCallback)

  }
}


