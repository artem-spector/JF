package com.jflop.server.rest.runtime.kafka;

import com.jflop.server.data.AgentJVM;
import com.jflop.server.data.JacksonSerdes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.jflop.server.TopicNames.DB_INGEST_TOPIC;

/**
 * TODO: Document!
 *
 * @author artem on 09/09/2017.
 */
@Component
public class DbIngestTopicConsumer extends KafkaTopicConsumer<AgentJVM, Map> {

    private static final Logger logger = Logger.getLogger(DbIngestTopicConsumer.class.getName());

    public DbIngestTopicConsumer() throws Exception {
        super(DB_INGEST_TOPIC, JacksonSerdes.AgentJVM().deserializer(), JacksonSerdes.Map().deserializer());
    }

    public List<Map> getData() {
        List<Map> res = new ArrayList<>();
        try {
            for (ConsumerRecord<AgentJVM, Map> record : readAll()) {
                res.add(record.value());
            }
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Failed reading data from topic " + DB_INGEST_TOPIC, e);
        }
        return res;
    }
}
