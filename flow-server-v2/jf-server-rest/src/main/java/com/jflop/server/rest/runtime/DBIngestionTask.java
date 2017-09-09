package com.jflop.server.rest.runtime;

import com.jflop.server.rest.persistency.ESClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TODO: Document!
 *
 * @author artem on 09/09/2017.
 */
@Component
public class DBIngestionTask {

    @Autowired
    private ESClient esClient;

    @Scheduled(fixedDelay = 3000)
    public void ingest() {
        System.out.println("Ingesting: esClient " + (esClient == null ? "is null" : " not null"));
    }
}
