package io.mdcatapult.klein.queue

import play.api.libs.json.{Format, Json}

object Msg {
  implicit val msgFormatter: Format[Msg] = Json.format[Msg]
}

case class Msg(id: String) extends Envelope {
  override def toJsonString(): String = ???
}
