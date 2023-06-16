package io.mdcatapult.klein.queue

import akka.stream.alpakka.amqp.QueueDeclaration

/**
 * An abstraction of a queue
 * name: name of the queue
 * durable: should the queue survive a server restart
 */
trait Subscribable {
  val name: String
  val durable: Boolean
  val queueDeclaration = QueueDeclaration(name).withDurable(
    durable
  )
}
