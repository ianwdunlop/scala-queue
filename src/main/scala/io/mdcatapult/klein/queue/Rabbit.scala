package io.mdcatapult.klein.queue

import akka.actor.ActorRef
import com.spingo.op_rabbit.{ConnectionParams, RabbitControl}
import com.spingo.op_rabbit.RabbitControl.Run

case object Liveness

class Rabbit(connection: Either[ConnectionParams, ActorRef]) extends RabbitControl(connection: Either[ConnectionParams, ActorRef]) {

  def this() = this(Left(ConnectionParams.fromConfig()))
  def this(connectionParams: ConnectionParams) = this(Left(connectionParams))
  def this(actorRef: ActorRef) = this(Right(actorRef))
  /**
    * Check the liveness of the connection to the queue.
    * Op-rabbit does this already so just return true if running.
    *
    * @return
    */
  def checkLiveness(): Boolean = {
    running == Run
  }

  override def receive = {
    case Liveness =>
      sender() ! checkLiveness()
    case x =>
      super.receive(x)
  }

}
