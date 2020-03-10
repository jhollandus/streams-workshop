package io.confluent.developer;

import io.confluent.demo.distribution.*;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroDeserializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.streams.KafkaStreams;

import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.integration.utils.EmbeddedKafkaCluster;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.*;
import tech.allegro.schema.json2avro.converter.JsonAvroConverter;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.emptyList;
import static org.junit.Assert.*;

//@Ignore
public class TransformStreamTest {

    private static final Logger LOG = LogManager.getLogger();
    private final static String TEST_CONFIG_FILE = "configuration/test.properties";

    @ClassRule
    public static EmbeddedKafkaCluster kafkaCluster = new EmbeddedKafkaCluster(1);

    KafkaProducer<SpecificRecord, SpecificRecord>  producer;
    Properties overridProps;
    Properties envProps;

    KafkaStreams kafkaStreams;
    TransformStream transformStream;

    @Before
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void before() throws Exception {
        kafkaCluster.createTopics("distribution-salesforce-evt-user.1", "distribution-salesforce-evt-campaign.1");
        MockSchemaRegistry.getClientForScope("test-scope");

        //create an avro producer
        overridProps = new Properties();
        overridProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaCluster.bootstrapServers());
        overridProps.put("schema.registry.url", "mock://test-scope");
        overridProps.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        overridProps.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        overridProps.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0L);


        var ser = new SpecificAvroSerializer<>();
        var serKey = new SpecificAvroSerializer<>();
        ser.configure(new HashMap(overridProps), false);
        serKey.configure(new HashMap(overridProps), true);

        producer = new KafkaProducer<>(overridProps, serKey, ser);

        envProps = new Properties();
        envProps.load(Files.newInputStream(Paths.get(TEST_CONFIG_FILE)));
        envProps.putAll(overridProps);
        transformStream = new TransformStream();

        var topology = transformStream.buildTopology(envProps);
        LOG.info(topology.describe());

        kafkaStreams = new KafkaStreams(topology, envProps);
        kafkaStreams.start();
    }

    @After
    public void after() throws Exception {
        kafkaStreams.close();
        producer.close();
        kafkaCluster.deleteAllTopicsAndWait(1000);
    }


    @Test
    public void testProduceUserSf() throws Exception {
        var props = new Properties();
        props.load(Files.newInputStream(Paths.get("configuration/dev.properties")));
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaCluster.bootstrapServers());
        props.put("schema.registry.url", "mock://test-scope");

        publishAvro("distribution-salesforce-evt-user.1", "data/user_sf.data",
                User_Sf_key.getClassSchema(), User_Sf_key.class,
                User_Sf.getClassSchema(), User_Sf.class);

        publishAvro("distribution-salesforce-evt-campaign.1", "data/campaign_sf.data",
                Campaign_Sf_key.getClassSchema(), Campaign_Sf_key.class,
                Campaign_Sf.getClassSchema(), Campaign_Sf.class);

        var consumer = avroConsumer(Arrays.asList("l1.distribution-cdc-campaign.1", "l1.distribution-cdc-userLookup.1"));
        Map<String, List<ConsumerRecord<? extends SpecificRecord, ? extends SpecificRecord>>> topicRecords = new HashMap<>();
        while(true) {
            var records = consumer.poll(Duration.ofMillis(5000));
            if(records.isEmpty()) {
                break;
            }
            records.forEach(r ->
                topicRecords.computeIfAbsent(r.topic(), k -> new LinkedList<>()).add(r));
        }
        assertEquals(5, topicRecords.computeIfAbsent("l1.distribution-cdc-campaign.1", k -> emptyList()).size());
        assertEquals(5, topicRecords.computeIfAbsent("l1.distribution-cdc-userLookup.1", k -> emptyList()).size());
        topicRecords.get("l1.distribution-cdc-campaign.1").forEach(r -> {
            var campaign = (Campaign_L1)r.value();
            assertTrue(campaign.getWorkerOwnerId().startsWith("w"));
            assertTrue(campaign.getWorkerCreatedById().startsWith("w"));
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public KafkaConsumer<? extends SpecificRecord, ? extends SpecificRecord> avroConsumer(Collection<String> topics) {

        var deser = new SpecificAvroDeserializer<>();
        var deserKey = new SpecificAvroDeserializer<>();
        deser.configure(new HashMap(overridProps), false);
        deserKey.configure(new HashMap(overridProps), true);
        var consumer = new KafkaConsumer<SpecificRecord, SpecificRecord>(
                envProps, deserKey, deser);

        var assignments = topics.stream()
                .flatMap(t -> consumer.partitionsFor(t).stream())
                .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                .collect(Collectors.toList());
        consumer.assign(assignments);
        consumer.seekToBeginning(assignments);

        return consumer;
    }

    public void publishAvro(String topic, String dataFilePath,
                            Schema keySchema, Class<? extends SpecificRecordBase> keyClass,
                            Schema valueSchema, Class<? extends SpecificRecordBase> valueClass) throws Exception {

        JsonAvroConverter jsonConverter = new JsonAvroConverter();
        Files.lines(Paths.get(dataFilePath)).forEach(avro -> {
            String[] kv = avro.split("\\|");
            SpecificRecord key = jsonConverter.convertToSpecificRecord(kv[0].getBytes(), keyClass, keySchema);
            SpecificRecord value = jsonConverter.convertToSpecificRecord(kv[1].getBytes(), valueClass, valueSchema);
            try {
                producer.send(new ProducerRecord<>(topic, key, value)).get();
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        });

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
