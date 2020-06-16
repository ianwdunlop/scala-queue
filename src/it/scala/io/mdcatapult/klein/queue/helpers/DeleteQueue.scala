package io.mdcatapult.klein.queue.helpers

import com.rabbitmq.client.Channel
import com.spingo.op_rabbit.MessageForPublicationLike

import scala.concurrent.{Future, Promise}

case class DeleteQueue(queueName: String) extends MessageForPublicationLike {

  val dropIfNoChannel = false

  private val _processedP = Promise[Unit]

  def processed: Future[Unit] = _processedP.future

  def apply(channel: Channel): Unit = {
    channel.queueDelete(queueName)
    _processedP.success(())
  }
}
