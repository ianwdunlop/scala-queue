package io.mdcatapult.klein.queue

/**
 * A container for a message sent to a queue.
 */
trait Envelope {

  /**
   *
   * @return the json string representation of the message eg "{\"message\": \"Do something\"}"
   */
  def toJsonString(): String
  // TODO could probably use something to automatically get the JSON representation of the implementing class
}
