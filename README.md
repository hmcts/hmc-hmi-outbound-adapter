# hmc-hmi-outbound-adapter

[![Build Status](https://travis-ci.org/hmcts/spring-boot-template.svg?branch=master)](https://travis-ci.org/hmcts/hmc-hmi-outbound-adapter)

## Notes

Since Spring Boot 2.1 bean overriding is disabled. If you want to enable it you will need to set `spring.main.allow-bean-definition-overriding` to `true`.

JUnit 5 is now enabled by default in the project. Please refrain from using JUnit4 and use the next generation

## Building and deploying the application

### Building the application

The project uses [Gradle](https://gradle.org) as a build tool. It already contains
`./gradlew` wrapper script, so there's no need to install gradle.

To build the project execute the following command:

```bash
  ./gradlew build
```

### Running the application

Create the image of the application by executing the following command:

```bash
  ./gradlew assemble
```

Create docker image:

```bash
  docker-compose build
```

Run the distribution (created in `build/install/hmc-hmi-outbound-adapter` directory)
by executing the following command:

```bash
  docker-compose up
```

This will start the API container exposing the application's port
(set to `4558` in this template app).

In order to test if the application is up, you can call its health endpoint:

```bash
  curl http://localhost:4558/health
```

You should get a response similar to this:

```
  {"status":"UP","diskSpace":{"status":"UP","total":249644974080,"free":137188298752,"threshold":10485760}}
```

### Alternative script to run application

To skip all the setting up and building, just execute the following command:

```bash
./bin/run-in-docker.sh
```

For more information:

```bash
./bin/run-in-docker.sh -h
```

Script includes bare minimum environment variables necessary to start api instance. Whenever any variable is changed or any other script regarding docker image/container build, the suggested way to ensure all is cleaned up properly is by this command:

```bash
docker-compose rm
```

It clears stopped containers correctly. Might consider removing clutter of images too, especially the ones fiddled with:

```bash
docker images

docker image rm <image-id>
```

There is no need to remove postgres and java or similar core images.

## Azure Service Bus & Local Testing

### Azure Service Bus

To enable publishing to an Azure Service Bus destination:

1. Set the Azure Service Bus connection string in the `HMC_SERVICE_BUS_CONNECTION_STRING` environment variable
1. Set the Azure Service Bus queue name in the `HMC_SERVICE_BUS_QUEUE` environment variable
1. Restart the application

## Developing

### Unit tests

To run all unit tests execute the following command:
```bash
  ./gradlew test
```

### Integration tests

To run all integration tests execute the following command:
```bash
  ./gradlew integration
```

### Code quality checks
We use [Checkstyle](http://checkstyle.sourceforge.net/).
To run all local checks execute the following command:

```bash
  ./gradlew check
```

Additionally, [SonarQube](https://sonarcloud.io/dashboard?id=uk.gov.hmcts.reform%3Ahmc-hmi-outbound-adapter)
analyses are performed on all remote code.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details

