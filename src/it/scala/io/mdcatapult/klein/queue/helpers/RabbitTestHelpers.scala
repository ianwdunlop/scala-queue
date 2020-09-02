package io.mdcatapult.klein.queue.helpers

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.rabbitmq.client.Channel
import com.spingo.op_rabbit.{MessageForPublicationLike, RabbitControl, RabbitMarshaller, RabbitUnmarshaller}
import com.spingo.scoped_fixtures.{ScopedFixtures, TestFixture}
import io.mdcatapult.klein.queue.Rabbit

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.language.postfixOps

object RabbitTestHelpers {

  val controlProps: Props = Props[Rabbit]
}

trait RabbitTestHelpers extends ScopedFixtures {

  implicit val timeout: Timeout = Timeout(125 seconds)

  val killConnection: MessageForPublicationLike = new MessageForPublicationLike {
    val dropIfNoChannel = true
    def apply(c: Channel): Unit = {
      c.getConnection.close()
    }
  }

  val actorSystemFixture: TestFixture[ActorSystem] = ScopedFixture[ActorSystem] { setter =>
    val actorSystem = ActorSystem("test")
    val status = setter(actorSystem)
    actorSystem.terminate()
    status
  }
  val rabbitControlFixture: TestFixture[ActorRef] = EagerFixture[ActorRef] {
    actorSystemFixture().actorOf(RabbitTestHelpers.controlProps)
  }

  implicit def actorSystem: ActorSystem = actorSystemFixture()
  def rabbitControl: ActorRef = rabbitControlFixture()
  def await[T](f: Future[T], duration: Duration = timeout.duration): T =
    Await.result(f, duration)

  // kills the rabbitMq connection in such a way that the system will automatically recover and reconnect;
  // synchronously waits for the connection to be terminated, and to be re-established
  def reconnect(rabbitMqControl: ActorRef)(implicit actorSystem: ActorSystem): Unit = {
    val connectionActor = await((rabbitMqControl ? RabbitControl.GetConnectionActor).mapTo[ActorRef])

    val done = Promise[Unit]
    actorSystem.actorOf(Props(new Actor {
      import akka.actor.FSM._
      override def preStart: Unit = {
        connectionActor ! SubscribeTransitionCallBack(self)
      }

      override def postStop: Unit = {
        connectionActor ! UnsubscribeTransitionCallBack(self)
      }

      def receive: Receive = {
        case CurrentState(_, state) if state.toString == "Connected" =>
          rabbitMqControl ! killConnection
          // because the connection shutdown cause is seen as isInitiatedByApplication, we need to send the Connect signal again to wake things back up.
          context.become {
            case Transition(_, from, to) if from.toString == "Connected" && to.toString == "Disconnected" =>
              connectionActor ! com.newmotion.akka.rabbitmq.ConnectionActor.Connect
              context.become {
                case Transition(_, from, to) if from.toString == "Disconnected" && to.toString == "Connected" =>
                  done.success(())
                  context stop self
              }
          }
      }
    }))

    await(done.future)
  }

  def deleteQueue(queueName: String): Unit = {
    val deleteQueue = DeleteQueue(queueName)
    rabbitControl ! deleteQueue
    await(deleteQueue.processed)
  }

  implicit val simpleIntMarshaller: RabbitMarshaller[Int] with RabbitUnmarshaller[Int] = new RabbitMarshaller[Int] with RabbitUnmarshaller[Int] {
    val contentType = "text/plain"
    val contentEncoding: Option[String] = Some("UTF-8")

    def marshall(value: Int): Array[Byte] =
      value.toString.getBytes

    def unmarshall(value: Array[Byte], contentType: Option[String], charset: Option[String]): Int = {
      new String(value).toInt
    }
  }
}
