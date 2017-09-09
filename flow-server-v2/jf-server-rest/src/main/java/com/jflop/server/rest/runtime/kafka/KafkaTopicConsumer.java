package com.jflop.server.rest.runtime.kafka;

import com.jflop.server.TopicNames;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Collections;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * TODO: Document!
 *
 * @author artem on 09/09/2017.
 */
abstract class KafkaTopicConsumer<K, V> {

    private static final Logger logger = Logger.getLogger(KafkaTopicConsumer.class.getName());

    private KafkaConsumer<K, V> consumer;

    KafkaTopicConsumer(String topicName, Deserializer<K> keyDeserializer, Deserializer<V> valueDeserializer) throws Exception {
        Properties topologyProp = new Properties();
        topologyProp.load(getClass().getClassLoader().getResourceAsStream("export.properties"));
        Properties props = new Properties();
        props.put("bootstrap.servers", topologyProp.getProperty("bootstrap.servers"));
        props.put("group.id", TopicNames.JF_REST_SERVER_CONSUMER_GROUP);
        props.put("enable.auto.commit", "true");
        consumer = new KafkaConsumer<>(props, keyDeserializer, valueDeserializer);
        consumer.subscribe(Collections.singletonList(topicName));
    }

    synchronized ConsumerRecords<K, V> readAll() {
        ConsumerRecords<K, V> res = consumer.poll(0);
        return res;
    }
}
