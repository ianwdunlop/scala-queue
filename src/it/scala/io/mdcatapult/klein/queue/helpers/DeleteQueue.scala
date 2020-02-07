package io.mdcatapult.klein.queue.helpers

import com.rabbitmq.client.Channel
import com.spingo.op_rabbit.MessageForPublicationLike

import scala.concurrent.{Future, Promise}

case class DeleteQueue(queueName: String) extends MessageForPublicationLike {
  private val _processedP = Promise[Unit]
  def processed: Future[Unit] = _processedP.future
  val dropIfNoChannel = false
  def apply(channel: Channel): Unit = {
    channel.queueDelete(queueName)
    _processedP.success(())
  }
}
