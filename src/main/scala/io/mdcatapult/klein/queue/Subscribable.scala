package io.mdcatapult.klein.queue

import akka.stream.alpakka.amqp.QueueDeclaration

trait Subscribable {
  val name: String
  val durable: Boolean
  val queueDeclaration = QueueDeclaration(name).withDurable(
    durable
  )
}
