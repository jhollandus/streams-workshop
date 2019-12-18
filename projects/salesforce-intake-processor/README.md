# Event Mapper

See https://www.enterpriseintegrationpatterns.com/patterns/messaging/MessagingMapper.html

## Summary

Transforms messages that may not adhere to the standardized messaging formats of the platform into the standardized format. This is done for several possible reasons:

 1. to make the messages easier to use (by making them more contextual)
 1. translating values into system indpendent ones (id translation for example)
 1. converting between message semantics (e.g. translate a domain entity change to an update command or delta only payload)

## Tutorial

https://kafka-tutorials.confluent.io/transform-a-stream-of-events/ksql.html

## Prerequisites

* bash shell
* docker
* docker-compose
* JDK 8
* internet connection (to pull docker images)

## Running

To begin running the kafka streams or KSQL applications you'll first need to start the docker-compose environment.

```
docker-compose up -d
```

Once started you can proceed to run your applications.

All application commands should be run from the root of the application directory, the same directory this README.md resides in.

### Kafka Streams

#### Start Kafka Streams

To start execute the `../../scripts/kstream start` script from the command line

```
../../scripts/kstream start
```

The stream will startup in detached mode. To see the logs issue this command `../../scripts/kstream logs`.

In another terminal you can publish corresponding data to the topics by executing: 

To publish user data

`../../scripts/avro publish distribution-salesforce-evt-user.1 User_Sf.avsc data/user_sf.data`

To publish campaign data

`../../scripts/avro publish distribution-salesforce-evt-campaign.1 Campaign_Sf.avsc data/campaign_sf.data`


After publishing data is complete or from another terminal you can execute:

`../../scripts/avro consume l1.distribution-cdc-campaign.1`

This will display the outputs of the stream to the final campaign topic.

The kafka streams app is packaged up in a docker container and deployed into the running docker-compose network.
To interact with it first find with `docker ps` then you can use any of the usual `docker` commands against it.
For example check out the logs by using `docker logs <container-name>` follow the logs by adding the `-f` switch `docker logs -f <container-name>`.

#### Stop Kafka Streams

Execute the script `../../scripts/kstreams stop`  to stop the streaming application at any time.

### KSQL

#### Start the KSQL Application

First you need to register the avro schems so ksql can find them to perform binding of queries to the data defined by them.
Do this by executing this command `../../scripts/avro register schemas.properties`.

Execute this command from this directory `../../scripts/ksql start`, this will deploy the KSQL application into the running docker-compose network.

As with the kafka streams app you can publish data and consume it the same. Note that the output of the KSQL application goes to topics prefixed by `ksql-`.

The entire ksql application can be found in the `queries.sql` file.

To see loggin run the `../../scripts/ksql logs` command.

#### Stop the KSQL Application

Stop by executing this command from this directory `../../scripts/ksql stop`.

### Cleaning Up

You will need to stop all associated docker containers this can be done by issuing stops to everything you've started then a final `docker-compose down`.
If an error pops up about not being able to cleanup the network than that indicates not all of the kstreams/ksql applications have been stopped.
