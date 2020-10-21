package io.mdcatapult.klein.queue

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import com.rabbitmq.client
import com.rabbitmq.client.AMQP.BasicProperties
import com.spingo.op_rabbit.{Binding, Directives, Message, RabbitErrorLogging, Subscription, Exchange => OpExchange, RecoveryStrategy => OpRecoveryStrategy}
import com.typesafe.config.ConfigFactory
import io.mdcatapult.klein.queue.helpers.RabbitTestHelpers
import io.mdcatapult.klein.queue.{RecoveryStrategy => MdcRecoveryStrategy}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.Random

/** A set of tests that has been adapted from OpRabbits own test consumerSpec.scala.  The focus here is on our own
  * RecoveryStrategy and demonstrating that the retry works.  Some tests that are peripheral to this aim has been discarded.
  * Other existing code has been modified to make it hopefully more readable, and sometimes to fix weaknesses in the
  * original test.
  */
class RecoveryStrategySpec extends TestKit(ActorSystem("QueueIntegrationTest", ConfigFactory.parseString(
"""
akka.loggers = ["akka.testkit.TestEventListener"]
"""))) with ImplicitSender with AnyFunSpecLike with Matchers with RabbitTestHelpers with ScalaFutures with Eventually {

  private val _queueName = ScopedFixture[String] { setter =>
    val name = s"test-queue-rabbit-control-${Math.random()}"
    deleteQueue(name)
    val r = setter(name)
    deleteQueue(name)
    r
  }

  /** Used as a way of ensuring that a unique queue name is available when required.
    */
  trait RabbitFixtures {
    val queueName: String = _queueName()
  }

  describe("consuming messages asynchronously") {
    it("receives and acks every message") {
      implicit val recoveryStrategy: OpRecoveryStrategy = MdcRecoveryStrategy(MdcRecoveryStrategy.errorQueue("err", sendErrors = true).apply)
      new RabbitFixtures {
        import RabbitErrorLogging.defaultLogger

        private val range = 0 to 100
        private val promises = range.map { _ => Promise[Int] }.toList
        private val generator = new Random(123)
        private val subscription = Subscription.run(rabbitControl) {
          import Directives._
          channel() {
            consume(queue(
              queueName,
              durable    = false,
              exclusive  = false,
              autoDelete = true),
              consumerTagPrefix = Some("testing123")) {
              body(as[Int]) { i =>
                Thread.sleep(Math.round(generator.nextDouble() * 100))
                promises(i).success(i)
                ack
              }
            }
          }
        }

        Await.result(subscription.initialized, 10.seconds)
        range foreach { i =>
          rabbitControl ! Message.queue(i, queueName)
        }
        val xs: Future[Seq[Int]] = Future.sequence(promises.map(_.future))
        whenReady(xs, Timeout(Span(20, Seconds))) { results: Seq[Int] =>
          results shouldBe range.toList
        }
      }
    }
  }

  describe("When checking for liveness") {
    it ("gets a reply") {
      val rb = actorSystem.actorOf(RabbitTestHelpers.controlProps)
      val a: Future[Any] = rb ? Liveness
      whenReady(a, Timeout(Span(20, Seconds))) { result =>
        result shouldBe true
      }
    }
  }

  describe("RecoveryStrategy limitedRedeliver") {
    /** Fixtures for message redelivery.  Errors received are tracked.  It is set-up to send 10 messages containing
      * the numbers 0 to 9.  Each time a message is received then the seen counter for that index is incremented.
      */
    trait RedeliveryFixtures {
      val retryCount: Int
      val queueName: String

      val range: Range.Inclusive = 0 to 9

      val errorCount = new AtomicInteger()

      implicit val logging: RabbitErrorLogging =
        (_: String, _: String, _: Throwable, _: String, _: client.Envelope, _: BasicProperties, _: Array[Byte]) => {
        errorCount.incrementAndGet()
      }

      /** Number of messages that have been failed.  This should NOT include the message sent to the error queue. */
      def errors: Int = errorCount.get()

      val seen: List[AtomicInteger] = range.map { _ => new AtomicInteger() }.toList

      private lazy val promises = range.map { _ => Iterator.continually(Promise[Int]).take(retryCount + 1).toVector }.toList

      /** A list of received message contents that is completed once all messages have been received.
        *
        * @return list of received message contents, one int per message
        */
      def messageDeliveries(): Future[List[Int]] =
        Future.sequence(promises.flatten.map(_.future))

      private val directExchange = OpExchange.direct("amq.direct", durable = false, autoDelete = true)

      /** A queue subscription that fails every message that is sent to it.  As an important side-effect it takes the
        * integer in the received message and increments the integer at that index in the seen list.
        *
        * @param recoveryStrategy our RecoveryStrategy that we are testing
        * @return subscription
        */
      def countAndRejectSubscription()(implicit recoveryStrategy: OpRecoveryStrategy): Subscription =
        Subscription {
          import Directives._

          val directBinding =
            Binding.direct(
              queue(queueName, durable = false, exclusive = false, autoDelete = true),
              OpExchange.passive(directExchange))

          channel(qos = 3) {
            consume(directBinding) {
              body(as[Int]) { i =>
                promises(i)(seen(i).getAndIncrement()).success(i)
                ack(Future.failed(new Exception("Such failure")))
              }
            }
          }
        }
    }

    it("attempts every message 2 times when retryCount = 1 and sends to error queue")(recoveryStrategyWithRetry(retryCount = 1, messagesPerRequest = 2))

    it("attempts every message 3 times when retryCount = 2 and sends to error queue")(recoveryStrategyWithRetry(retryCount = 2, messagesPerRequest = 3))

    it("attempts every message 4 times when retryCount = 3 and sends to error queue")(recoveryStrategyWithRetry(retryCount = 3, messagesPerRequest = 4))

    /** Constructs a RecoveryStrategy to have a given number of retries.  Messages are then fired against the queue which
      * will all be rejected.  It then verifies that the expected number of messages (including to the error queue)
      * hve been sent and that the correct number of errors have been received.
      *
      * @param retryCount defines the number of retries that the recovery strategy should attempt
      * @param messagesPerRequest the number of messages that should be sent by each request, namely 1 per retry + 1 to the error queue
      * @return unimportant as the key testing OneForOneStrategy happens within its constructor, but if needed calls could be made to it to help diagnose a testing issue
      */
    def recoveryStrategyWithRetry(retryCount: Int, messagesPerRequest: Int): RedeliveryFixtures = {
      val _retryCount = retryCount


      new RedeliveryFixtures with RabbitFixtures {
        val errorCounter: AtomicInteger = new AtomicInteger()
        // Error queue to send message to after retries exhausted
        Subscription.run(rabbitControl) {
          import Directives._
          channel() {
            consume(queue(
              "err",
              durable    = false,
              exclusive  = false,
              autoDelete = true),
              consumerTagPrefix = Some("error123")) {
              body(as[Int]) { _ =>
                errorCounter.incrementAndGet()
                ack
              }
            }
          }
        }
        override val retryCount: Int = _retryCount

        implicit val recoveryStrategy: OpRecoveryStrategy =
          MdcRecoveryStrategy(
            MdcRecoveryStrategy.errorQueue(
              errorQueueName = "err",
              sendErrors = true,
              retryCount = retryCount
            ).apply)

        private val subscriptionDef = countAndRejectSubscription()

        private val subscription = subscriptionDef.run(rabbitControl)

        subscription.initialized.map {_ =>
          range foreach { i => rabbitControl ! Message.queue(i, queueName) }
        }

        whenReady(messageDeliveries(), Timeout(Span(10, Seconds))) { messagesReceivedContents: List[Int] =>
          val expectedMessagesSent: Int = range.length * (retryCount + 1)

          messagesReceivedContents should have length expectedMessagesSent
          (seen map (_.get)).distinct should be(List(retryCount + 1))
          eventually(Timeout(Span(20, Seconds))) {
            errors should be(expectedMessagesSent)
          }
          eventually(Timeout(Span(20, Seconds))){
            errorCounter.get() should equal(10)
          }
        }
      }
    }
  }
}
