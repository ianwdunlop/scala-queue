package io.mdcatapult.klein.queue

import akka.Done
import akka.actor.ActorSystem
import akka.stream.alpakka.amqp._
import akka.stream.alpakka.amqp.scaladsl.{AmqpFlow, AmqpSink, AmqpSource, CommittableReadResult}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.rabbitmq.client.AMQP
import com.typesafe.config.Config
import io.mdcatapult.klein.queue.Queue.RetryHeader

import java.util
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try


// These could be [T, U] but think we only need [HandlerResult] since we can't do anything with the PrefetchMsg because it is wrapped in
// the CommitableReadResult. The raw CommitableReadResult
// needs to get passed into the "businessLogic" ie the handle method and de-serialized on the handler side rather than the old
// way of deserializing before sending to the handler.
// M = message you send to the queue through send(envelope: M)
// T is the response through subscribe
case class Queue[M <: Envelope, T](name: String,
                                                    consumerName: Option[String] = None,
                                                    topics: Option[String] = None,
                                                    persistent: Boolean = true,
                                                    errorQueueName: Option[String] = None)
                                                   (implicit actorSystem: ActorSystem,
                                                    config: Config,
                                                    ex: ExecutionContext) {

  // the durable setting must match the existing queue, or an exception is thrown when using it
  private val queueDeclaration = QueueDeclaration(name).withDurable(true) // withArguments method to specify exchange?
  private val maxRetries = config.getInt("queue.max-retries")

  // get a load of connection params from the config
  // TODO use exception handler for retries?
  private val amqpDetailsConnectionProvider =
  AmqpDetailsConnectionProvider(host = config.getString("queue.host"), port = 5672)
    .withVirtualHost(config.getString("queue.virtual-host"))
    .withHostsAndPorts(Seq((config.getString("queue.host"), config.getInt("queue.port"))))
    .withCredentials(AmqpCredentials(config.getString("queue.username"), config.getString("queue.password")))
    .withConnectionTimeout(config.getInt("queue.connection-timeout"))

  // define a source using the connection provider, queue name and queue declaration
  private val amqpSourceSettings =
    NamedQueueSourceSettings(amqpDetailsConnectionProvider, name)
      .withDeclaration(queueDeclaration)
      .withAckRequired(true)


  private val writeSettings = AmqpWriteSettings(amqpDetailsConnectionProvider)
    .withRoutingKey(name)
    .withDeclaration(queueDeclaration)
    .withBufferSize(10)
    .withConfirmationTimeout(200.millis)


  val amqpFlow: Flow[WriteMessage, WriteResult, Future[Done]] = AmqpFlow.withConfirm(writeSettings)

  val amqpSink: Sink[WriteMessage, Future[Done]] =
    AmqpSink(
      writeSettings
    )

  /**
   * Resend the message if the retries have not been exhausted by nacking the old one, incrementing
   * the retry header on a copy of it and sending it again
   *
   * @param cm The message
   * @return
   */
  def getRetries(cm: CommittableReadResult): Future[ReadResult] = {
    val headers: util.Map[String, AnyRef] = cm.message.properties.getHeaders
    val retriesFromHeader = Try {
      val retryHeader = headers.get(RetryHeader)
      retryHeader.toString.toInt
    }
    val numRetries = retriesFromHeader.map(retries => retries + 1).getOrElse(1)
    println(s"Retries for ${cm.message.bytes.utf8String} is $numRetries")
    if (numRetries > maxRetries) {
      println(s"${cm.message.bytes.utf8String} has exceeded retries so nacking")
      cm.nack(requeue = false).map(_ => cm.message)
    }
    else {
      println(s"nacking ${cm.message.bytes.utf8String}")
      for {
        _ <- {
          val header: Map[String, Object] = Map(RetryHeader -> Integer.valueOf(numRetries))
          val jHeader = header.asJava

          // overwrite the existing header
          val amqpBasicProps: AMQP.BasicProperties =
            cm.message.properties.builder().headers(jHeader).build()

          // send a new message, TODO can we reliably call .get on the try below?
          val sendResult: Future[Done] = send(Try(cm.message.envelope.asInstanceOf[M]).get, Some(amqpBasicProps))
          sendResult
        }
        nackRes <- {
          cm.nack(requeue = false).map(_ => cm.message)
        }
      } yield nackRes
    }
  }

  /**
   * Subscribe runs the flow between the source rabbit queue and the sink. In between it passes any
   * messages to the businessLogic which returns a future containing the original message and a true/fail to indicate success
   * or failure
   *
   * @param businessLogic
   * @param concurrent
   * @return
   */
  def subscribe(businessLogic: CommittableReadResult => Future[(CommittableReadResult, Option[T])],
                               concurrent: Int = 1)
  : Future[Seq[ReadResult]] = AmqpSource.committableSource(
    settings = amqpSourceSettings,
    bufferSize = concurrent
  )
    .mapAsync(1)(
      // Success or fail?
      msg => businessLogic(msg)
    )
    // check case for success/fail and ack nack
    .mapAsync(1) {
      case
        // hr is prefetch result. Not sure what we do with it
        (cm, Some(hr)) => {
        println(s"${cm.message.bytes.utf8String} is successful. Acking")
        cm.ack().map(_ => cm.message)
      }
      case
        (cm, Some(e: Exception)) => getRetries(cm)
    }
    .runWith(Sink.seq)

  def send(envelope: M, properties: Option[AMQP.BasicProperties] = None): Future[Done] = {
    val result: Future[Done] = Source.single(envelope)
      .map(
        {
          message => {
            val writeMessage = WriteMessage(ByteString(envelope.toString))
            writeMessage.withProperties(persistMessages(properties))
          }
        }
      )
      .runWith(amqpSink)
    println(s"sent $envelope")
    result
  }

  /**
   * Check if messages are to be persisted and add delivery mode property
   * @param properties
   * @return
   */
  private def persistMessages(properties: Option[AMQP.BasicProperties]) = {
    // At the moment the properties have to be present
    val persistedProps = if (persistent) {
      properties.get.builder().deliveryMode(2).build()
    } else {
      properties.get.builder().deliveryMode(1).build()
    }
    persistedProps
  }
}

object Queue {
  val RetryHeader = "x-retry"
}
