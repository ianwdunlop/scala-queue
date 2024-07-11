/*
 * Copyright 2024 Medicines Discovery Catapult
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.doclib.queue

 import org.apache.pekko.Done
 import com.rabbitmq.client.AMQP
 import com.rabbitmq.client.AMQP.BasicProperties

 import scala.concurrent.Future

trait Sendable[T <: Envelope] {
  val name: String
  val persistent: Boolean

  /**
   * Send a message of type T to the the queue
   * @param envelope
   * @param properties
   * @return
   */
   def send(envelope: T, properties: Option[AMQP.BasicProperties] = None): Future[Done]

  /** Check if messages are to be persisted and add delivery mode property
   *
   * @param properties
   * @return
   */
  def persistMessages(properties: Option[AMQP.BasicProperties]): BasicProperties = {
    val basicProperties = properties match {
      case None => new BasicProperties.Builder().build()
      case Some(props) => props
    }
    val persistedProps = if (persistent) {
      basicProperties.builder().deliveryMode(2).build()
    } else {
      basicProperties.builder().deliveryMode(1).build()
    }
    persistedProps
  }
}
