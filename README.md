# Scala Queue

A RabbitMQ queue abstraction with JSON logging, retries & optional error queuing.

## Message flow
If a message fails it is retried a maximum of 3 times. For each retry the original message is nack'd and a new message
sent containing a header `x-retry` with the number of retries. After the final retry the message is nack'd and
optionally sent to the error queue for handling by the Errors Consumer.

## Persistent messages
Messages sent to a `Queue` are always persisted by adding a `DeliveryModePersistence(true)` property.

## Config
If you want errors to be sent to the errors queue after retries then set the following in your application.conf. The default is false.
```
error {
  queue = true
}
```

