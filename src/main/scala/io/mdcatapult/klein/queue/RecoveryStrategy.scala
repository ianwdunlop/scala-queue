package io.mdcatapult.klein.queue

import java.time.LocalDateTime

import com.rabbitmq.client.Channel
import com.spingo.op_rabbit.Directives.{ack, extract, property}
import com.spingo.op_rabbit.RecoveryStrategy.{`x-original-exchange`, `x-original-routing-key`, `x-retry`}
import com.spingo.op_rabbit.properties.HeaderValue.{StringHeaderValue => Value}
import com.spingo.op_rabbit.properties.{Header, MessageProperty, PimpedBasicProperties}
import com.spingo.op_rabbit.{Binding, Directives, Handler, properties, Exchange => OpExchange, Queue => OpQueue, RecoveryStrategy => OpRecoveryStrategy}
import io.mdcatapult.klein.queue.Converter.toStrings

class RecoveryStrategyWrap(fn: (String, Channel, Throwable) => Handler) extends OpRecoveryStrategy {
  def apply(queueName: String, ch: Channel, ex: Throwable): Handler = fn(queueName, ch, ex)
}

object RecoveryStrategy {

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

    def apply(queueName: String, channel: Channel, exception: Throwable): Handler =
      getRetryCount {
        case thisRetryCount if thisRetryCount < retryCount =>
          enqueue(queueName, channel, List(`x-retry`(thisRetryCount + 1)))
        case _ =>
          enqueue(
            errorQueueName,
            channel,
            List(
              Header("x-consumer", Value(consumerName.getOrElse("undeclared"))),
              Header("x-queue", Value(queueName)),
              Header("x-datetime", Value(LocalDateTime.now().toString)),
              Header("x-exception", Value(exception.getClass.getCanonicalName)),
              Header("x-message", Value(exception.getMessage)),
              Header("x-stack-trace", Value(toStrings(exception).mkString("\n"))),
            )
          )
      }
  }
}
