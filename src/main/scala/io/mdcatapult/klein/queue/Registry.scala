package io.mdcatapult.klein.queue

import akka.actor.ActorSystem
import com.typesafe.config.Config
import play.api.libs.json.Format

import scala.collection.mutable

class Registry[T <: Envelope]()(implicit actorSystem: ActorSystem, config: Config, formatter: Format[T]) {

  var register: mutable.Map[String, Sendable[T]] = mutable.Map[String, Sendable[T]]()

  def get(name: String, consumerName: Option[String] = None, topics: Option[String] = None): Sendable[T] = {
    if (!register.contains(name)) register(name) = Queue[T](name, consumerName, topics)
    register(name)
  }

}
