package io.confluent.developer;

import io.confluent.demo.distribution.User_Sf;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroDeserializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import static org.junit.Assert.*;

import org.apache.kafka.streams.test.ConsumerRecordFactory;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

//@Ignore
public class TransformStreamTest {

    private final static String TEST_CONFIG_FILE = "configuration/test.properties";

    @Before
    public void before() {

       // MockSchemaRegistry.getClientForScope("test-scope");
    }

    public SpecificAvroSerializer<User_Sf> makeSerializer(Properties envProps) {
        SpecificAvroSerializer<User_Sf> serializer = new SpecificAvroSerializer<>();

        Map<String, String> config = new HashMap<>();
        config.put("schema.registry.url", envProps.getProperty("schema.registry.url"));
        serializer.configure(config, false);

        return serializer;
    }

    public SpecificAvroDeserializer<User_Sf> makeDeserializer(Properties envProps) {
        SpecificAvroDeserializer<User_Sf> deserializer = new SpecificAvroDeserializer<>();

        Map<String, String> config = new HashMap<>();
        config.put("schema.registry.url", envProps.getProperty("schema.registry.url"));
        deserializer.configure(config, false);

        return deserializer;
    }

    private List<User_Sf> readOutputTopic(TopologyTestDriver testDriver,
                                              String topic,
                                              Deserializer<String> keyDeserializer,
                                              SpecificAvroDeserializer<User_Sf> valueDeserializer) {
        List<User_Sf> results = new ArrayList<>();

        while (true) {
            ProducerRecord<String, User_Sf> record = testDriver.readOutput(topic, keyDeserializer, valueDeserializer);

            if (record != null) {
                results.add(record.value());
            } else {
                break;
            }
        }

        return results;
    }

    @Test
    public void testProduceUserSf() throws Exception {
      TransformStream ts = new TransformStream();
      User_Sf user = User_Sf.newBuilder()
              .setDurableId("did01")
              .setId("1")
              .setOperation("CREATE")
              .setSocialSecurity("111-111-1111")
              .setWorkerId("wid01")
              .build();

      assertNotNull(user);

       Properties props = new Properties();
       props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
       props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8081");

       SpecificAvroSerializer<User_Sf> ser = new SpecificAvroSerializer<>();
       ser.configure(new HashMap(props), false);

        KafkaProducer<String, User_Sf> producer = new KafkaProducer<String, User_Sf>(props, new StringSerializer(), ser);
        producer.send(new ProducerRecord<>("distribution-salesforce-evt-user.1", user.getId(), user)).get();
    }


//    @Test
//    public void testTransformStream() throws IOException {
//        TransformStream ts = new TransformStream();
//        Properties envProps = ts.loadEnvProperties(TEST_CONFIG_FILE);
//        Properties streamProps = ts.buildStreamsProperties(envProps);
//
//        String inputTopic = envProps.getProperty("input.topic.name");
//        String outputTopic = envProps.getProperty("output.topic.name");
//
//        Topology topology = ts.buildTopology(envProps);
//        TopologyTestDriver testDriver = new TopologyTestDriver(topology, streamProps);
//
//        Serializer<String> keySerializer = Serdes.String().serializer();
//        SpecificAvroSerializer<RawMovie> valueSerializer = makeSerializer(envProps);
//
//        Deserializer<String> keyDeserializer = Serdes.String().deserializer();
//        SpecificAvroDeserializer<Movie> valueDeserializer = makeDeserializer(envProps);
//
//        ConsumerRecordFactory<String, RawMovie> inputFactory = new ConsumerRecordFactory<>(keySerializer, valueSerializer);
//
//        List<RawMovie> input = new ArrayList<>();
//        input.add(RawMovie.newBuilder().setId(294).setTitle("Die Hard::1988").setGenre("action").build());
//        input.add(RawMovie.newBuilder().setId(354).setTitle("Tree of Life::2011").setGenre("drama").build());
//        input.add(RawMovie.newBuilder().setId(782).setTitle("A Walk in the Clouds::1995").setGenre("romance").build());
//        input.add(RawMovie.newBuilder().setId(128).setTitle("The Big Lebowski::1998").setGenre("comedy").build());
//
//        List<Movie> expectedOutput = new ArrayList<>();
//        expectedOutput.add(Movie.newBuilder().setTitle("Die Hard").setId(294).setReleaseYear(1988).setGenre("action").build());
//        expectedOutput.add(Movie.newBuilder().setTitle("Tree of Life").setId(354).setReleaseYear(2011).setGenre("drama").build());
//        expectedOutput.add(Movie.newBuilder().setTitle("A Walk in the Clouds").setId(782).setReleaseYear(1995).setGenre("romance").build());
//        expectedOutput.add(Movie.newBuilder().setTitle("The Big Lebowski").setId(128).setReleaseYear(1998).setGenre("comedy").build());
//
//        for (RawMovie rawMovie : input) {
//            testDriver.pipeInput(inputFactory.create(inputTopic, rawMovie.getTitle(), rawMovie));
//        }
//
//        List<Movie> actualOutput = readOutputTopic(testDriver, outputTopic, keyDeserializer, valueDeserializer);
//
//        assertEquals(expectedOutput, actualOutput);
//    }
//
}
