package com.jflop.server.rest.runtime.kafka;

import com.jflop.server.TopicNames;
import com.jflop.server.data.AgentJVM;
import com.jflop.server.data.JacksonSerdes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 5/14/17
 */
@Component
public class CommandTopicConsumer extends KafkaTopicConsumer<AgentJVM, Map> implements InitializingBean {

    private Map<AgentJVM, List<Map>> agentFeatureCommands = new HashMap<>();

    public CommandTopicConsumer() throws Exception {
        super(TopicNames.COMMAND_OUT_TOPIC, JacksonSerdes.AgentJVM().deserializer(), JacksonSerdes.Map().deserializer());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        getFeatureCommands(new AgentJVM("none", "none", "none")); // warm up the consumer
    }

    public List<Map> getFeatureCommands(AgentJVM key) {
        for (ConsumerRecord<AgentJVM, Map> record : readAll()) {
            agentFeatureCommands.computeIfAbsent(record.key(), k -> new ArrayList<>()).add(record.value());
        }

        return agentFeatureCommands.remove(key);
    }
}
