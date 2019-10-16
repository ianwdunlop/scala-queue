package io.mdcatapult.klein.queue

import akka.actor.ActorSystem
import play.api.libs.json.{Format, Reads, Writes}

import scala.collection.mutable
import scala.concurrent.ExecutionContext

class Registry[T <: Envelope]()
                             (implicit actorSystem: ActorSystem, ex: ExecutionContext, reader: Reads[T], writer: Writes[T], formatter: Format[T]) {

  var register: mutable.Map[String, Sendable[T]] = mutable.Map[String, Sendable[T]]()

  def get(name: String, consumerName: Option[String] = None, topics: Option[String] = None): Sendable[T] = {
    if (!register.contains(name)) register(name) = Queue[T](name, consumerName, topics)
    register(name)
  }

}
