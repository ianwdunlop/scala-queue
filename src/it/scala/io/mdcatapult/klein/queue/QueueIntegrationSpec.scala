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
import scala.util.{Failure, Success, Try}

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

  "A message to a queue" can "fail" in {

    val queueName = "failure-queue"

    val queue = createQueueOnly(queueName, true, None, true)
    val businessLogic: CommittableReadResult => Future[(CommittableReadResult, Try[Message])] = { committableReadResult =>
      Future((committableReadResult, Failure(new Exception("boom"))))
    }
    queue.subscribe(businessLogic)

    // give the queue a second or 2 to sort itself out
    Thread.sleep(3000)
    val res = queue.send(Message("Fail me"))
    whenReady(res) {
      r => println(s"result is ${r.toString}")
    }

  }

  "A queue" should "can be subscribed to and read from" in {

    // Note that we need to include readResult topic if we want the queue to be created
    val consumerName = Option(config.getString("op-rabbit.topic-exchange-name"))
    val queueName = "persistent-test-queue"

    val queue = createQueueOnly(queueName, true, consumerName, true)
    val businessLogic: CommittableReadResult => Future[(CommittableReadResult, Try[Message])] = { committableReadResult =>
      val msg = Message((math.random < 0.5).toString)
      Future((committableReadResult, Success(msg)))
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