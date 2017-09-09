package com.jflop.server.rest.runtime.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.util.Properties;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 5/6/17
 */
public abstract class KafkaTopicProducer<K, V> {

    private String topicName;
    private final Producer<K, V> producer;

    KafkaTopicProducer(String topicName, Serializer<K> keySerializer, Serializer<V> valueSerializer) throws IOException {
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
        props.put("key.serializer", keySerializer.getClass().getName());
        props.put("value.serializer", valueSerializer.getClass().getName());

        producer = new KafkaProducer<>(props);
    }

    public void send(K key, V value) {
        producer.send(new ProducerRecord<>(topicName, key, value));
    }
}
