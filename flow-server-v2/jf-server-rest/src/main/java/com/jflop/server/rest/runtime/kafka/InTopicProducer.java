package com.jflop.server.rest.runtime.kafka;

import com.jflop.server.data.AgentJVM;
import com.jflop.server.data.JacksonSerdes;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

import static com.jflop.server.TopicNames.IN_TOPIC;

/**
 * TODO: Document!
 *
 * @author artem on 09/09/2017.
 */
@Component
public class InTopicProducer extends KafkaTopicProducer<AgentJVM, Map> {
    InTopicProducer() throws IOException {
        super(IN_TOPIC, JacksonSerdes.AgentJVM().serializer(), JacksonSerdes.Map().serializer());
    }
}
