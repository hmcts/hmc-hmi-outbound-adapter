# Queue Writer Utility
Utility for writing messages to an Azure Service Bus queue.

writes one message at a time then exits.

## Running

Set up the following environment variables:
- `CONNECTION_STRING`: Azure Service Bus connection string for the namespace containing the queue
- `QUEUE`: Queue name

Then to receive a single message:

`./gradlew run`
