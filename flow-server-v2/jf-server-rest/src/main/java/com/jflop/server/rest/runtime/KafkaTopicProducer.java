package com.jflop.server.rest.runtime;

import com.jflop.server.data.AgentJVM;
import com.jflop.server.data.JacksonSerdes;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 5/6/17
 */
public class KafkaTopicProducer {

    private String topicName;
    private final Producer<AgentJVM, Map<String, Object>> producer;

    public KafkaTopicProducer(String topicName) throws IOException {
        this.topicName = topicName;
        Properties topologyProp = new Properties();
        topologyProp.load(getClass().getClassLoader().getResourceAsStream("export.properties"));

        Properties props = new Properties();
        props.put("bootstrap.servers", topologyProp.getProperty("bootstrap.servers"));
        props.put("acks", "all");
        props.put("retries", 0);
        props.put("batch.size", 16384);
        props.put("linger.ms", 1);
        props.put("buffer.memory", 33554432);
        props.put("key.serializer", JacksonSerdes.AgentJVM().serializer().getClass().getName());
        props.put("value.serializer", JacksonSerdes.Map().serializer().getClass().getName());

        producer = new KafkaProducer<>(props);
    }

    public void send(AgentJVM key, Map<String, Object> value) {
        producer.send(new ProducerRecord<>(topicName, key, value));
    }
}
