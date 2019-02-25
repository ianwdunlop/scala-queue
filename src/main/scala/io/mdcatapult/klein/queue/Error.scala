package io.mdcatapult.klein.queue

import play.api.libs.json._
import com.spingo.op_rabbit.PlayJsonSupport

object Error {
  implicit val errorReader: Reads[Error] = Json.reads[Error]
  implicit val errorWriter: Writes[Error] = Json.writes[Error]
  implicit val errorFormatter: Format[Error] = Json.format[Error]
}

case class Error(
                  id: String,
                  source: String,
                  message: String,
                  trace: Option[String] = None
                ) extends Envelope

