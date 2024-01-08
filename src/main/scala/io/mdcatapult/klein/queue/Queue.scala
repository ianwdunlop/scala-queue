package io.mdcatapult.klein.queue

import akka.Done
import akka.stream.Materializer
import akka.stream.alpakka.amqp._
import akka.stream.alpakka.amqp.scaladsl.{AmqpSink, AmqpSource, CommittableReadResult}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.rabbitmq.client.AMQP
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.mdcatapult.klein.queue.Queue.RetryHeader

import java.util
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object Queue {
  val RetryHeader = "x-retry"
}
/**
 * Create a Queue using params from config that receives messages M and returns T as part of the "business logic" response.
 * M = message you send to the queue through send,
 * T is part of the response through subscribe
 * @param name name of the queue
 * @param durable should the queue be persisted if the message server restarts
 * @param consumerName what service we expect to read the messages. This shoudl be used in log messages to make it clear what service sent a message that caused an error
 * @param topics legacy. Currently not needed
 * @param persistent should the message be persisted in case the server restarts
 * @param errorQueueName legacy. Not used in this queue version. Was previously used to send messages to error queue for storage.
 * @param materializer used by akka. Implicitly provided via import
 * @param config Provides config used by the queue
 * @param ex Thread pool used by the akka queue model. Provided via implicit import
 * @tparam M Message type that the queue receives
 * @tparam T Response type from the "business logic" provided via subscribe
 */
case class Queue[M <: Envelope, T] (
    name: String,
    durable: Boolean = true,
    consumerName: Option[String] = None,
    topics: Option[String] = None,
    persistent: Boolean = true,
    errorQueueName: Option[String] = None
)(implicit val materializer: Materializer, config: Config, ex: ExecutionContext)
    extends Subscribable
      with LazyLogging
      with Sendable[M] {

  private val maxRetries = config.getInt("queue.max-retries")

  private val amqpDetailsConnectionProvider =
    AmqpDetailsConnectionProvider(
      host = config.getString("queue.host"),
      port = config.getInt("queue.port")
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
    val retryHeader = headers match {
      case null => Success(0)
      case _ => Try {
        val retryHeader = headers.get(RetryHeader)
        retryHeader.toString.toInt
      }
    }
    val numRetries = retryHeader match {
      case Success(retries) => retries
      case Failure(_) => 0
    }

    logger.info(s"Retries for ${cm.message.bytes.utf8String} is $numRetries")
    if (numRetries > maxRetries) {
      // nack it after retries exhausted. Could log it out as well. In previous versions
      // we wrote out some Json to the db about the failure but not sure we should
      logger.error(s"${cm.message.bytes.utf8String} has exceeded retries so nacking without requeue. Exception was ${e.getMessage}")
      cm.nack(requeue = false).map(_ => cm.message)
    } else {
      for {
        _ <- {
          val header: Map[String, Object] = Map(
            RetryHeader -> Integer.valueOf(numRetries + 1)
          )
          val jHeader = header.asJava

          // overwrite the existing header
          val amqpBasicProps: AMQP.BasicProperties =
            cm.message.properties.builder().headers(jHeader).build()

          // send a new message
          val msg = ByteString(cm.message.bytes.utf8String)
          val sendResult: Future[Done] = sendRetry(msg, Some(amqpBasicProps))
          sendResult
        }
        // we've sent a new message with retry count in header so nack the old one.
        // TODO any way we can add the retries to header and just nack it?
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
          // Also gets result from the business logic. Not sure what we want do with it
          (cm, Success(_)) => {
        logger.info(s"${cm.message.bytes.utf8String} was successful.")
        // if business logic failed then nack, otherwise ack
        cm.ack().map(_ => cm.message)
      }
      case (cm, Failure(e)) => {
        logger.error(s"${cm.message.bytes.utf8String} was not successful.", e)
        getRetries(cm, e)
      }
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
    result
  }

  /**
   * Send a message based on a previously failed one
   * @param message
   * @param properties
   * @return
   */
  def sendRetry(
            message: ByteString,
            properties: Option[AMQP.BasicProperties]
          ): Future[Done] = {
    val result: Future[Done] = Source
      .single(message)
      .map(
        { message => {
          val writeMessage = WriteMessage(message)
          writeMessage.withProperties(persistMessages(properties))
        }
        }
      )
      .runWith(amqpSink)
    result
  }

//  /** Check if messages are to be persisted and add delivery mode property
//   *
//   * @param properties
//   * @return
//   */
//  override def persistMessages(properties: Option[AMQP.BasicProperties]): BasicProperties = {
//    val basicProperties = properties match {
//      case None => new BasicProperties.Builder().build()
//      case Some(props) => props
//    }
//    val persistedProps = if (persistent) {
//      basicProperties.builder().deliveryMode(2).build()
//    } else {
//      basicProperties.builder().deliveryMode(1).build()
//    }
//    persistedProps
//  }
}
