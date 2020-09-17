package io.mdcatapult.klein.queue

import com.rabbitmq.client.Channel
import com.spingo.op_rabbit.Directives._
import com.spingo.op_rabbit.RecoveryStrategy.{`x-original-exchange`, `x-original-routing-key`, `x-retry`}
import com.spingo.op_rabbit.properties.{MessageProperty, PimpedBasicProperties}
import com.spingo.op_rabbit.{Binding, Directives, Handler, properties, Exchange => OpExchange, Queue => OpQueue, RecoveryStrategy => OpRecoveryStrategy}
import com.typesafe.scalalogging.LazyLogging

class RecoveryStrategyWrap(fn: (String, Channel, Throwable) => Handler) extends OpRecoveryStrategy {
  def apply(queueName: String, ch: Channel, ex: Throwable): Handler = fn(queueName, ch, ex)
}

object RecoveryStrategy extends LazyLogging {

  def apply(fn: (String, Channel, Throwable) => Handler): OpRecoveryStrategy = {
    new RecoveryStrategyWrap(fn)
  }

  def errorQueue(
                  errorQueueName: String,
                  consumerName: Option[String] = None,
                  exchange: OpExchange[OpExchange.Direct.type] = OpExchange.default,
                  retryCount: Int = 3,
                ): OpRecoveryStrategy = new OpRecoveryStrategy {

    private val getRetryCount = property(`x-retry`) | Directives.provide(0)

    private def genRetryBinding(queueName: String): Binding =
      Binding.direct(
        OpQueue.passive(
          OpQueue(
            queueName,
            durable = true,
            arguments = List[properties.Header]()
          )),
        exchange)

    private def enqueue(queueName: String, channel: Channel, props: Seq[MessageProperty]): Handler =
      (extract(identity) & OpRecoveryStrategy.originalRoutingKey & OpRecoveryStrategy.originalExchange) {
        (delivery, rk, x) =>
          val binding: Binding = genRetryBinding(queueName)
          binding.declare(channel)
          channel.basicPublish(exchange.exchangeName,
            binding.queueName,
            delivery.properties ++ props ++ List(
              `x-original-routing-key`(rk),
              `x-original-exchange`(x)),
            delivery.body)
          ack
      }

    /*
    Re-queue the message if there are any retries available. Otherwise log the error.
     */
    def apply(queueName: String, channel: Channel, exception: Throwable): Handler =
      getRetryCount {
        case thisRetryCount if thisRetryCount < retryCount =>
          enqueue(queueName, channel, List(`x-retry`(thisRetryCount + 1)))
        case _ =>
          body(Directives.as[String]) { message =>
            logger.error(s"Retries exhausted for Queue: $queueName, Exception:  $exception, Original Message: $message")
            nack()
          }
      }
  }
}
