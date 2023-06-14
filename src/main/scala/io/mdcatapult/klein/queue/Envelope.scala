package io.mdcatapult.klein.queue

trait Envelope {

  def toJsonString(): String
}
