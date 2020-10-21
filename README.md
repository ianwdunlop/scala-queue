# Scala Queue

A RabbitMQ queue abstraction with JSON logging, retries & optional error queuing.

## Config
If you want errors to be sent to the errors queue after retries then set the following in your application.conf. The default is false.
```
error {
  queue = true
}
```
