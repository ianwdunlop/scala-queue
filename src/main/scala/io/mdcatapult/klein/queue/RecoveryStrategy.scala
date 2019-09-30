package io.mdcatapult.klein.queue

import java.time.LocalDateTime

import com.rabbitmq.client.Channel
import com.spingo.op_rabbit.Directives.{ack, extract}
import com.spingo.op_rabbit.Queue.ModeledArgs.{`x-expires`, `x-message-ttl`}
import com.spingo.op_rabbit.RecoveryStrategy.{`x-original-exchange`, `x-original-routing-key`}
import com.spingo.op_rabbit.properties.{Header, HeaderValue, PimpedBasicProperties}
import com.spingo.op_rabbit.{Binding, Handler, properties, Exchange => OpExchange, Queue => OpQueue, RecoveryStrategy => OpRecoveryStrategy}

import scala.concurrent.duration.{FiniteDuration, _}

class RecoveryStrategyWrap(fn: (String, Channel, Throwable) => Handler) extends OpRecoveryStrategy {
  def apply(queueName: String, ch: Channel, ex: Throwable): Handler = fn(queueName, ch, ex)
}

object RecoveryStrategy {

  def apply(fn: (String, Channel, Throwable) => Handler): OpRecoveryStrategy = {
    new RecoveryStrategyWrap(fn)
  }

  def errorQueue(
                errorQueue: String,
                consumerName: Option[String] = None,
                exchange: OpExchange[OpExchange.Direct.type] = OpExchange.default,
                defaultTTL: FiniteDuration = 7.days,
                errorQueueProperties: List[properties.Header] = Nil,
                ) = OpRecoveryStrategy {

    (queueName, channel, exception) =>
      (extract(identity) & OpRecoveryStrategy.originalRoutingKey & OpRecoveryStrategy.originalExchange) { (delivery, rk, x) =>
        val binding = Binding.direct(
          OpQueue.passive(
            OpQueue(
              errorQueue,
              durable = true,
              arguments = List[properties.Header](
                `x-message-ttl`(defaultTTL),
                `x-expires`(defaultTTL * 2)) ++
                errorQueueProperties
            )
          ),
          exchange)

        binding.declare(channel)
        channel.basicPublish(exchange.exchangeName,
          binding.queueName,
          delivery.properties ++ Seq(
            Header("x-consumer", HeaderValue.StringHeaderValue(consumerName.getOrElse("undeclared"))),
            Header("x-queue", HeaderValue.StringHeaderValue(queueName)),
            Header("x-datetime", HeaderValue.StringHeaderValue(LocalDateTime.now().toString)),
            Header("x-exception", HeaderValue.StringHeaderValue(exception.getClass.getCanonicalName)),
            Header("x-message", HeaderValue.StringHeaderValue(exception.getMessage)),
            Header("x-stack-trace", HeaderValue.StringHeaderValue(exception.getStackTrace.map((s: StackTraceElement) => s.toString).mkString("\n"))),
            `x-original-routing-key`(rk),
            `x-original-exchange`(x)),
          delivery.body)
        ack
      }
  }
}
