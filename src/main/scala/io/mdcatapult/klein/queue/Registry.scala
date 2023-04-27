package io.mdcatapult.klein.queue

import akka.actor.ActorSystem
import com.typesafe.config.Config

import scala.collection.mutable
import scala.concurrent.ExecutionContext

class Registry[M <: Envelope, T]()(implicit
    actorSystem: ActorSystem,
    config: Config,
    ex: ExecutionContext
) {

  var register: mutable.Map[String, Sendable[M]] =
    mutable.Map[String, Sendable[M]]()

  def get(
      name: String,
      consumerName: Option[String] = None,
      topics: Option[String] = None,
      persistent: Boolean = true,
      errorQueueName: Option[String] = None
  ): Sendable[M] = {
    if (!register.contains(name))
      register(name) = Queue[M, T](
        name,
        consumerName,
        topics,
        persistent,
        errorQueueName
      )
    register(name)
  }

}
