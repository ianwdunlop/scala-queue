package io.mdcatapult.klein.queue

import java.time.LocalDateTime

import com.rabbitmq.client.Channel
import com.spingo.op_rabbit.Directives.{ack, extract, property}
import com.spingo.op_rabbit.Queue.ModeledArgs.{`x-expires`, `x-message-ttl`}
import com.spingo.op_rabbit.RecoveryStrategy.{`x-original-exchange`, `x-original-routing-key`, `x-retry`, nack}
import com.spingo.op_rabbit.properties.{Header, HeaderValue, MessageProperty, PimpedBasicProperties}
import com.spingo.op_rabbit.{Binding, Directives, Handler, properties, Exchange => OpExchange, Queue => OpQueue, RecoveryStrategy => OpRecoveryStrategy}

import scala.concurrent.duration.{FiniteDuration, _}

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
                  defaultTTL: FiniteDuration = 7.days,
                  errorQueueProperties: List[properties.Header] = Nil,
                  retryCount: Int = 3,
                  onAbandon: OpRecoveryStrategy = nack(false),
                  redeliverDelay: FiniteDuration = 10.seconds,
                  retryQueueProperties: List[properties.Header] = Nil
                ): OpRecoveryStrategy = new OpRecoveryStrategy {

    private val getRetryCount = property(`x-retry`) | Directives.provide(0)

    private def genErrorsBinding: Binding =
      Binding.direct(
        OpQueue.passive(
          OpQueue(
            errorQueueName,
            durable = true,
            arguments = List[properties.Header](
              `x-message-ttl`(defaultTTL),
              `x-expires`(defaultTTL * 2)) ++
              errorQueueProperties)),
        exchange)


    private def genRetryBinding(queueName: String): Binding =
      Binding.direct(
        OpQueue.passive(
          OpQueue(
            queueName,
            durable = true,
            arguments = List[properties.Header]()
          )),
        exchange)


    private def enqueue(queueName: String, channel: Channel, binding: Binding, props: Seq[MessageProperty] = List()): Handler =
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


    private def getStackTrace(exception: Throwable): List[String] = {
      val trace = exception.getStackTrace.map((s: StackTraceElement) => s.toString).toList
      Option(exception.getCause) match {
        case Some(cause) => trace ++ List("Exception Caused By:", cause.getMessage) ++ getStackTrace(cause)
        case None => trace
      }
    }


    def apply(queueName: String, channel: Channel, exception: Throwable): Handler = {
      getRetryCount {
        case thisRetryCount if thisRetryCount < retryCount =>
          enqueue(queueName, channel, genRetryBinding(queueName), List(`x-retry`(thisRetryCount + 1)))
        case _ =>
          enqueue(errorQueueName, channel, genErrorsBinding, List(
            Header("x-consumer", HeaderValue.StringHeaderValue(consumerName.getOrElse("undeclared"))),
            Header("x-queue", HeaderValue.StringHeaderValue(queueName)),
            Header("x-datetime", HeaderValue.StringHeaderValue(LocalDateTime.now().toString)),
            Header("x-exception", HeaderValue.StringHeaderValue(exception.getClass.getCanonicalName)),
            Header("x-message", HeaderValue.StringHeaderValue(exception.getMessage)),
            Header("x-stack-trace", HeaderValue.StringHeaderValue(getStackTrace(exception).mkString("\n"))),
          ))
      }
    }
  }
}
