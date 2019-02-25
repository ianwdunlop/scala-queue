package io.mdcatapult.klein.queue

import play.api.libs.json.{Format, Json, Reads, Writes}


object Msg {
  implicit val msgReader: Reads[Msg] = Json.reads[Msg]
  implicit val msgWriter: Writes[Msg] = Json.writes[Msg]
  implicit val msgFormatter: Format[Msg] = Json.format[Msg]
}

case class Msg(id: String) extends Envelope

