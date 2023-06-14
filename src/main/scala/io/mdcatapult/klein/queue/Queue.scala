package io.mdcatapult.klein.queue

import akka.Done
import akka.stream.Materializer
import akka.stream.alpakka.amqp._
import akka.stream.alpakka.amqp.scaladsl.{AmqpSink, AmqpSource, CommittableReadResult}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.AMQP.BasicProperties
import com.typesafe.config.Config
import io.mdcatapult.klein.queue.Queue.RetryHeader

import java.util
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

// These could be [T, U] but think we only need [HandlerResult] since we can't do anything with the PrefetchMsg because it is wrapped in
// the CommitableReadResult. The raw CommitableReadResult
// needs to get passed into the "businessLogic" ie the handle method and de-serialized on the handler side rather than the old
// way of deserializing before sending to the handler.
// M = message you send to the queue through send(envelope: M)
// T is the response through subscribe
case class Queue[M <: Envelope, T](
    name: String,
    durable: Boolean = true,
    consumerName: Option[String] = None,
    topics: Option[String] = None,
    persistent: Boolean = true,
    errorQueueName: Option[String] = None
)(implicit val materializer: Materializer, config: Config, ex: ExecutionContext)
    extends Subscribable
    with Sendable[M] {

  // the durable setting must match the existing queue, or an exception is thrown when using it
//  private val queueDeclaration = QueueDeclaration(name).withDurable(
//    durable
//  ) // withArguments method to specify exchange?
  private val maxRetries = config.getInt("queue.max-retries")

  // get a load of connection params from the config
  // TODO use exception handler for retries?
  private val amqpDetailsConnectionProvider =
    AmqpDetailsConnectionProvider(
      host = config.getString("queue.host"),
      port = 5672
    )
      .withVirtualHost(config.getString("queue.virtual-host"))
      .withHostsAndPorts(
        Seq((config.getString("queue.host"), config.getInt("queue.port")))
      )
      .withCredentials(
        AmqpCredentials(
          config.getString("queue.username"),
          config.getString("queue.password")
        )
      )
      // Assuming milliseconds - API docs don't say
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

//  val amqpFlow: Flow[WriteMessage, WriteResult, Future[Done]] =
//    AmqpFlow.withConfirm(writeSettings)

  val amqpSink: Sink[WriteMessage, Future[Done]] =
    AmqpSink(
      writeSettings
    )

  /** Resend the message if the retries have not been exhausted by nacking the
    * old one, incrementing the retry header on a copy of it and sending it
    * again
    *
    * @param cm
    *   The message
    * @return
    */
  private def getRetries(cm: CommittableReadResult, e: Throwable): Future[ReadResult] = {
    val headers: util.Map[String, AnyRef] = cm.message.properties.getHeaders
    val retriesFromHeader = Try {
      val retryHeader = headers.get(RetryHeader)
      retryHeader.toString.toInt
    }
    val numRetries = retriesFromHeader.map(retries => retries + 1).getOrElse(1)
    println(s"Retries for ${cm.message.bytes.utf8String} is $numRetries")
    if (numRetries > maxRetries) {
      // nack it after retries exhausted. Could log it out as well. In previous versions
      // we wrote out some Json about the failure but not sure we should
      println(s"${cm.message.bytes.utf8String} has exceeded retries so nacking without requeue. Exception was ${e.getMessage}")
      cm.nack(requeue = false).map(_ => cm.message)
    } else {
      println(s"nacking ${cm.message.bytes.utf8String}")
      for {
        _ <- {
          val header: Map[String, Object] = Map(
            RetryHeader -> Integer.valueOf(numRetries)
          )
          val jHeader = header.asJava

          // overwrite the existing header
          val amqpBasicProps: AMQP.BasicProperties =
            cm.message.properties.builder().headers(jHeader).build()

          // send a new message, TODO can we reliably call .get on the try below?
          val sendResult: Future[Done] = send(
            Try(cm.message.envelope.asInstanceOf[M]).get,
            Some(amqpBasicProps)
          )
          sendResult
        }
        // we've sent a new message with retry count in header so nack the old one. TODO any way we can add the retries to header and just nack it?
        nackRes <- {
          cm.nack(requeue = false).map(_ => cm.message)
        }
      } yield nackRes
    }
  }

  /** Subscribe runs the flow between the source rabbit queue and the sink. In
    * between it passes any messages to the businessLogic which returns a future
    * containing the original message and a true/fail to indicate success or
    * failure
    *
    * @param businessLogic
    * @param concurrent
    * @return
    */
  def subscribe(
      businessLogic: CommittableReadResult => Future[
        (CommittableReadResult, Try[T])
      ],
      concurrent: Int = 1
  ): Future[Seq[ReadResult]] = AmqpSource
    .committableSource(
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
          // Also gets Some(result) from the business logic. Not sure what we want do with it
          (cm, Success(hr)) => {
        println(s"${cm.message.bytes.utf8String} is successful. Acking")
        println(s"hr is: ${hr.toString}")
        // if business logic failed then nack, otherwise ack
        cm.ack().map(_ => cm.message)
      }
      case (cm, Failure(e)) => getRetries(cm, e)
    }
    .runWith(Sink.seq)

  def send(
      envelope: M,
      properties: Option[AMQP.BasicProperties]
  ): Future[Done] = {
    val result: Future[Done] = Source
      .single(envelope)
      .map(
        { message =>
          {
            val writeMessage = WriteMessage(ByteString(envelope.toJsonString()))
            writeMessage.withProperties(persistMessages(properties))
          }
        }
      )
      .runWith(amqpSink)
    println(s"sent $envelope")
    result
  }

  /** Check if messages are to be persisted and add delivery mode property
    * @param properties
    * @return
    */
   override def persistMessages(properties: Option[AMQP.BasicProperties]) = {
    // At the moment create the properties rather than use what is passed in
     val properties = new BasicProperties.Builder()
    val persistedProps = if (persistent) {
      properties.deliveryMode(2).build()
    } else {
      properties.deliveryMode(1).build()
    }
    persistedProps
  }
}

object Queue {
  val RetryHeader = "x-retry"
}
