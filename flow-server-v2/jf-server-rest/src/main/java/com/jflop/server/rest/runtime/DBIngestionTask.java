package com.jflop.server.rest.runtime;

import com.jflop.server.rest.persistency.ESClient;
import com.jflop.server.rest.runtime.kafka.DbIngestTopicConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * TODO: Document!
 *
 * @author artem on 09/09/2017.
 */
@Component
public class DBIngestionTask {

    private static final Logger logger = Logger.getLogger(DBIngestionTask.class.getName());

    @Autowired
    private ESClient esClient;

    @Autowired
    private DbIngestTopicConsumer consumer;

    @Scheduled(fixedDelay = 3000)
    public void ingest() {
        List<Map> data = consumer.getData();
        logger.info("ingest " + (data == null ? "is null" : data.size() + " records"));
    }

}
