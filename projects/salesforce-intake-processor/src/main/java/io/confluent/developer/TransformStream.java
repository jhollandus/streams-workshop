package io.confluent.developer;

import io.confluent.demo.distribution.*;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class TransformStream {

    public Properties buildStreamsProperties(Properties envProps) {
        Properties props = new Properties();

        props.put(StreamsConfig.APPLICATION_ID_CONFIG, envProps.getProperty("application.id"));
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, envProps.getProperty("bootstrap.servers"));
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, envProps.getProperty("schema.registry.url"));

        return props;
    }

    public Topology buildTopology(Properties envProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final String campaignSfTopic = envProps.getProperty("topic.campaign_sf.name");
        final String campaignL1Topic = envProps.getProperty("topic.campaign_l1.name");
        final String userSfTopic = envProps.getProperty("topic.user_sf.name");
        final String userLookupTopic = envProps.getProperty("topic.userLookup_l1.name");

        KStream<User_Sf_key, User_Sf> userSfStream = builder.stream(userSfTopic);
        KStream<Campaign_Sf_key, Campaign_Sf> caimpaignSfStream = builder.stream(campaignSfTopic);
        GlobalKTable<UserLookup_L1_key, UserLookup_L1> userLookupTable = builder.globalTable(userLookupTopic,
                Materialized.with(
                        specificAvroSerde(UserLookup_L1_key.class, envProps),
                        specificAvroSerde(UserLookup_L1.class, envProps)));


        //Drop sensitive information and create a lookup topic for users
        userSfStream.map((key, userSf) -> {
               UserLookup_L1 userLookup = UserLookup_L1.newBuilder()
                        .setDurableId(userSf.getDurableId())
                        .setId(userSf.getId())
                        .setWorkerId(userSf.getWorkerId())
                        .build();
               return KeyValue.pair(new UserLookup_L1_key(userSf.getId()), userLookup);
        })
                .to(userLookupTopic);


        KStream<Campaign_L1_key, Campaign_L1> compaignL1Stream = caimpaignSfStream
                .map((key, campSf) -> KeyValue.pair(new Campaign_L1_key(campSf.getId()), campSf))
                .join(userLookupTable,
                        (key, campaign) -> new UserLookup_L1_key(campaign.getCreatedById()),
                        (campaign, user) -> Campaign_L1.newBuilder()
                                .setId(campaign.getId())
                                .setCreatedById(campaign.getCreatedById())
                                .setDurableId(campaign.getDurableId())
                                .setOwnerId(campaign.getOwnerId())
                                .setOperation(campaign.getOperation())
                                .setRecordType(campaign.getRecordType())
                                .setWorkerCreatedById(user.getWorkerId())
                                .build())
                .join(userLookupTable,
                        (key, campaign) -> new UserLookup_L1_key(campaign.getOwnerId()),
                        (campaign, user) -> Campaign_L1.newBuilder(campaign)
                                .setWorkerOwnerId(user.getWorkerId())
                                .build());
        compaignL1Stream.to(campaignL1Topic);

        return builder.build();
    }

    private <T extends SpecificRecord> SpecificAvroSerde<T> specificAvroSerde(Class<T> clazz, Properties envProps) {
        SpecificAvroSerde<T> avroSerde = new SpecificAvroSerde<>();

        final HashMap<String, String> serdeConfig = new HashMap<>();
        serdeConfig.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                envProps.getProperty("schema.registry.url"));
        avroSerde.configure(serdeConfig, false);

        return avroSerde;
    }

    public void createTopics(Properties envProps) {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", envProps.getProperty("bootstrap.servers"));
        AdminClient client = AdminClient.create(config);

        List<NewTopic> topics = new ArrayList<>();

        topics.add(new NewTopic(
                envProps.getProperty("input.topic.name"),
                Integer.parseInt(envProps.getProperty("input.topic.partitions")),
                Short.parseShort(envProps.getProperty("input.topic.replication.factor"))));

        topics.add(new NewTopic(
                envProps.getProperty("output.topic.name"),
                Integer.parseInt(envProps.getProperty("output.topic.partitions")),
                Short.parseShort(envProps.getProperty("output.topic.replication.factor"))));

        client.createTopics(topics);
        client.close();
    }

    public Properties loadEnvProperties(String fileName) throws IOException {
        Properties envProps = new Properties();
        FileInputStream input = new FileInputStream(fileName);
        envProps.load(input);
        input.close();

        return envProps;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("This program takes one argument: the path to an environment configuration file.");
        }

        TransformStream ts = new TransformStream();
        Properties envProps = ts.loadEnvProperties(args[0]);
        Properties streamProps = ts.buildStreamsProperties(envProps);


        //build topology with properties
        Topology topology = ts.buildTopology(envProps);
        System.out.println("" + topology.describe());

        // create topics outside of application
        // ts.createTopics(envProps);

        final KafkaStreams streams = new KafkaStreams(topology, streamProps);
        final CountDownLatch latch = new CountDownLatch(1);

        // Attach shutdown handler to catch Control-C.
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}
