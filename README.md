# Scala Queue

A RabbitMQ queue abstraction with JSON logging, retries & optional error queuing.

## Message flow
If a message fails it is retried a maximum of 3 times. For each retry the original message is nack'd and a new message
sent containing a header `x-retry` with the number of retries. After the final retry the message is nack'd and
optionally sent to the error queue for handling by the Errors Consumer.

## Persistent messages
Messages sent to a `Queue` are always persisted by adding a `DeliveryModePersistence(true)` property.

## Error Queue Recovery Strategy
Pass an `errorQueue = Option("error-queue-name")` into a `Queue` to publish exception info to the error queue after 3 retries.

## Testing
```bash
docker-compose up -d
sbt clean test it:test
```

