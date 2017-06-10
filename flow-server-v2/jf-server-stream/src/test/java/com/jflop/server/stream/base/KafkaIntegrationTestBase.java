package com.jflop.server.stream.base;

import com.jflop.server.data.AgentJVM;
import com.jflop.server.data.JacksonSerdes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.KafkaStreams;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * TODO: Document!
 *
 * @author artem on 23/05/2017.
 */
public abstract class KafkaIntegrationTestBase {

    private static final Logger logger = LoggerFactory.getLogger(KafkaIntegrationTestBase.class);

    protected interface AwaitCondition<V> {
        V checkCondition();
    }

    public static final String IN_TOPIC = "process-in-topic";
    public static final String COMMAND_OUT_TOPIC = "command-topic";

    protected static Properties topologyProperties;
    private static KafkaProducer producer;
    private static KafkaConsumer commandsTopicConsumer;

    private String testAppPrefix;

    private String appId;
    private StreamsApplication application;
    private KafkaStreams streams;

    private Set<AgentJVM> sentKeys = new HashSet<>();

    protected KafkaIntegrationTestBase(String testAppPrefix) {
        this.testAppPrefix = testAppPrefix;
    }

    @BeforeClass
    public static void createProducerAndConsumers() throws IOException {
        topologyProperties = new Properties();
        topologyProperties.load(KafkaIntegrationTestBase.class.getClassLoader().getResourceAsStream("export.properties"));
        producer = createProducer();
        commandsTopicConsumer = createConsumer(COMMAND_OUT_TOPIC);
    }

    @Before
    public void startStreams() throws IOException, InterruptedException {
        appId = testAppPrefix;
        application = createApplication(appId);
        streams = application.build();
        streams.cleanUp();
        long start = System.currentTimeMillis();
        streams.start();
        Boolean isRunning = await(() -> streams.state().isRunning(), 1000);
        assertTrue(isRunning);
        System.out.println("Application " + appId + " started in " + (System.currentTimeMillis() - start)/1000f + " sec.");
    }

    @After
    public void stopStreams() {
        long start = System.currentTimeMillis();
        clearProcessorStates();
        streams.close(3, TimeUnit.SECONDS);
        application.clearApplicationDir();
        streams = null;
        System.out.println("Application " + appId + " stopped in " + (System.currentTimeMillis() - start)/1000f + " sec.");
    }

    private static KafkaProducer createProducer() throws IOException {
        Properties props = new Properties();
        props.put("bootstrap.servers", topologyProperties.getProperty("bootstrap.servers"));
        props.put("acks", "all");
        props.put("retries", 0);
        props.put("batch.size", 16384);
        props.put("linger.ms", 1);
        props.put("buffer.memory", 33554432);

        return new KafkaProducer<>(props, JacksonSerdes.AgentJVM().serializer(), JacksonSerdes.Map().serializer());
    }

    private static KafkaConsumer<AgentJVM, Map> createConsumer(String topicName) throws IOException {
        Properties props = new Properties();
        props.put("bootstrap.servers", topologyProperties.getProperty("bootstrap.servers"));
        props.put("group.id", "TEST");
        props.put("enable.auto.commit", "false");
        KafkaConsumer<AgentJVM, Map> consumer = new KafkaConsumer<>(props, JacksonSerdes.AgentJVM().deserializer(), JacksonSerdes.Map().deserializer());
        consumer.subscribe(Arrays.asList(topicName));
        return consumer;
    }

    protected void sendInputRecord(AgentJVM key, Map<String, Object> value) {
        logger.info("Sending input record: " + key + "->" + value);
        producer.send(new ProducerRecord<>(IN_TOPIC, key, value));
        producer.flush();
        sentKeys.add(key);
    }

    protected ConsumerRecords<AgentJVM, Map<String, Map<String, Object>>> pollCommands(long timeout) {
        synchronized (commandsTopicConsumer) {
            ConsumerRecords records = commandsTopicConsumer.poll(timeout);
            commandsTopicConsumer.commitSync();
            return records;
        }
    }

    protected Map<String, Map<String, Object>> extractAgentCommands(ConsumerRecords<AgentJVM, Map<String, Map<String, Object>>> all, AgentJVM key) {
        Map<String, Map<String, Object>> res = new HashMap<>();
        for (ConsumerRecord<AgentJVM, Map<String, Map<String, Object>>> record : all) {
            if (record.key().equals(key)) res.putAll(record.value());
        }
        return res;
    }

    protected <T> T await(AwaitCondition<T> condition, long timeoutMs) {
        long timeout = System.currentTimeMillis() + timeoutMs;
        T res;
        while ((res = condition.checkCondition()) == null && System.currentTimeMillis() < timeout)
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // ignore
            }
        return res;
    }

    protected void clearProcessorStates() {
        Map<String, Object> clearTuple = new HashMap<>();
        clearTuple.put(TestStatefulProcessor.CLEAR_STATE_STORESS, "true");
        for (AgentJVM key : sentKeys) {
            sendInputRecord(key, clearTuple);
        }
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    protected abstract StreamsApplication createApplication(String appId);

}
