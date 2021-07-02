# Queue Writer Utility
Utility for writing messages to an Azure Service Bus queue.

Sends one message at a time then exits.

## Running

Set up the following environment variables:
- `CONNECTION_STRING`: Azure Service Bus connection string for the namespace containing the queue
- `QUEUE`: Queue name

## Optional

- Update json string on line 46 of QueueWriter.java

Set up the following environment variables:
- `MESSAGE_TYPE`: Currently supported values are REQUEST_HEARING, AMEND_HEARING or DELETE_HEARING
- `HEARING_ID`: required for AMEND_HEARING and DELETE_HEARING requests

Then to write a single message:

`./gradlew run`
