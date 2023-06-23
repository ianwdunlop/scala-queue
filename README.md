# Scala Queue

A RabbitMQ queue abstraction with retries. Uses [Alpakka AMQP connector](https://doc.akka.io/docs/alpakka/current/amqp.html)

## Message flow
If a message fails it is retried a maximum of 3 times. For each retry the original message is nack'd and a new message
sent containing a header `x-retry` with the number of retries. After the final retry the message is nack'd and the error logged.

## Persistent messages
Messages sent to a `Queue` are persisted by default but can be changed to `false` when creating a queue.

## Creating a queue
A queue has "business logic" associated with it that is run when a client subscribes to a queue and receives a message. The business logic returns a `Success` or `Failure`.  
In the example here the message is a simple string and the response also a string. The `M` type of the queue must implement the `io.mdcatapult.klein.queue.Envelope` trait so that we can get 
the json representation of the message when we send it. You need to override the `toJsonString` method in your `Envelope` sub class and return whatever is appropriate.

```scala
object Message {
  implicit val msgFormatter: Format[Message] = Json.format[Message]
}
case class Message(message: String) extends Envelope {
  override def toJsonString(): String = {
    Json.toJson(this).toString()
  }
}

val queueName = "a-queue"
val queue = Queue[Message, String](name="a-queue", durable=true, consumerName=None, persistent=true)

val businessLogic: CommittableReadResult => Future[(CommittableReadResult, Try[Message])] = { committableReadResult =>
  // do something and send result back
  Future((committableReadResult, Success("It worked")))
}

queue.subscribe(businessLogic)
queue.send("Do something for me")
// this will trigger the business logic to be run
```
The `CommitableReadResult` in the response contains the original message which will be acked or nacked as appropriate.

A queue is created with the type of message it can receive and the response it returns from the business logic. In this doclib specific example the client expects a 
`PrefetchMessage` to be received and the business logic would return a `PrefetchResult`.
```scala
Queue[PrefetchMessage, PrefechResult]
```
See the integration tests for some examples.

## Config
There are various config options that are used when creating a queue. These can be overriden on the command line via environment variables:

* **QUEUE_MAX_RETRIES** - max number of retries to attempt (default: 3)
* **RABBITMQ_HOST** - RabbitMQ host name (default: localhost)
* **RABBITMQ_VHOST** - RabbitMQ virtual host (default: doclib)
* **RABBITMQ_USERNAME** - RabbitMQ username (default: doclib)
* **RABBITMQ_PASSWORD** - RabbitMQ username (default: doclib)
* **RABBITMQ_PORT** - RabbitMQ API port (default: 5672)


## Testing
```bash
docker-compose up -d
sbt clean it:test
```

Version 1.9 and below use op-rabbit. Versions greater than 1.9 use Alpakka.

