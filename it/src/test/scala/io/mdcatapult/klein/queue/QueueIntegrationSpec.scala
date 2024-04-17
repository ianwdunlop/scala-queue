/*
 * Copyright 2024 Medicines Discovery Catapult
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.mdcatapult.klein.queue

import akka.actor._
import akka.stream.alpakka.amqp.scaladsl.CommittableReadResult
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import monix.execution.atomic.AtomicInt
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import play.api.libs.json.{Format, Json}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.collection.mutable.Map

/**
 * Use Play Json implicit formatter to convert class to Json string
 */
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

  "A message to a queue" can "fail and is then retried" in {

    val queueName = "failure-queue"
    val failure_Count: AtomicInt = AtomicInt(0)
    // keep track of failures per message
    val failureCount: Map[String, Int] = scala.collection.mutable.Map[String, Int]("{\"message\":\"Fail me\"}" -> 0, "{\"message\":\"Fail me 2\"}" -> 0, "{\"message\":\"Fail me 3\"}" -> 0)
    val queue = createQueueOnly(queueName, true, None, true)
    val businessLogic: CommittableReadResult => Future[(CommittableReadResult, Try[Message])] = { committableReadResult =>
      failure_Count.add(1)
      failureCount(committableReadResult.message.bytes.utf8String) = failureCount(committableReadResult.message.bytes.utf8String) + 1
      // fail the message and force a retry
      Future((committableReadResult, Failure(new Exception("boom"))))
    }
    queue.subscribe(businessLogic)

    // give the queue a second or 2 to sort itself out
    Thread.sleep(3000)
    queue.send(Message("Fail me"))

    queue.send(Message("Fail me 2"))

    queue.send(Message("Fail me 3"))

    eventually (timeout(Span(25, Seconds))) {
      failureCount("{\"message\":\"Fail me\"}" ) should be >= 4
      failure_Count.get() should be >= 12
    }

  }

  "A queue" should "can be subscribed to and read from" in {

    // Note that we need to include readResult topic if we want the queue to be created
    val queueName = "persistent-test-queue"
    val count: AtomicInt = AtomicInt(0)

    val queue = createQueueOnly(queueName, true, Some("test-consumer"), true)
    val businessLogic: CommittableReadResult => Future[(CommittableReadResult, Try[Message])] = { committableReadResult =>
      val msg = Message((math.random() < 0.5).toString)
      count.add(1)
      Future((committableReadResult, Success(msg)))
    }
    queue.subscribe(businessLogic)

    // give the queue a second or 2 to sort itself out
    Thread.sleep(3000)
    val res = queue.send(Message("I am a message"))
    whenReady(res) {
      r => r.toString should be ("Done")
    }
    // should only be one trip through the business logic
    eventually (timeout(Span(25, Seconds))) {
      count.get() should be (1)
    }

  }
}