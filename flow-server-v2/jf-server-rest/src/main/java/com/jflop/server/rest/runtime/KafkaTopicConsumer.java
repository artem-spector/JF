package com.jflop.server.rest.runtime;

import com.jflop.server.data.AgentJVM;
import com.jflop.server.data.JacksonSerdes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.io.IOException;
import java.util.*;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 5/14/17
 */
public class KafkaTopicConsumer {

    private final KafkaConsumer<AgentJVM, Map> consumer;
    private Map<AgentJVM, List<Map>> agentFeatureCommands = new HashMap<>();

    public KafkaTopicConsumer(String topicName, String consumerGroup) throws IOException {
        Properties topologyProp = new Properties();
        topologyProp.load(getClass().getClassLoader().getResourceAsStream("export.properties"));

        Properties props = new Properties();
        props.put("bootstrap.servers", topologyProp.getProperty("bootstrap.servers"));
        props.put("group.id", consumerGroup);
        props.put("enable.auto.commit", "false");
        consumer = new KafkaConsumer<>(props, JacksonSerdes.AgentJVM().deserializer(), JacksonSerdes.Map().deserializer());
        consumer.subscribe(Arrays.asList(topicName));
    }

    public List<Map> getFeatureCommands(AgentJVM key) {
        synchronized (consumer) {
            ConsumerRecords<AgentJVM, Map> records = consumer.poll(0);
            for (ConsumerRecord<AgentJVM, Map> record : records) {
                agentFeatureCommands.computeIfAbsent(record.key(), k -> new ArrayList<>()).add(record.value());
            }
            consumer.commitSync();
        }

        List<Map> commandsMap = agentFeatureCommands.remove(key);
        System.out.println("read commands: " + commandsMap);
        return commandsMap;
    }
}
