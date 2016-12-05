package com.jflop.server.runtime;

import com.jflop.server.persistency.IndexTemplate;
import org.springframework.stereotype.Component;

/**
 * Index for the data that is created by server itself.
 *
 * @author artem
 *         Date: 11/26/16
 */
@Component
public class ProcessedDataIndex extends IndexTemplate {

    private static final String PROCESSED_DATA_INDEX = "jf-processed-data";

    public ProcessedDataIndex() {
        super(PROCESSED_DATA_INDEX + "-template", PROCESSED_DATA_INDEX + "*");
    }

    @Override
    public String indexName() {
        return PROCESSED_DATA_INDEX;
    }

}
