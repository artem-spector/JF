package com.jflop.server.runtime;

import com.jflop.server.persistency.DocType;
import com.jflop.server.persistency.IndexTemplate;
import com.jflop.server.runtime.data.JvmMonitorData;
import org.springframework.stereotype.Component;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 10/12/16
 */
@Component
public class RawDataIndex extends IndexTemplate {

    private static final String RAW_DATA_INDEX = "jf-raw-data";

    public RawDataIndex() {
        super(RAW_DATA_INDEX + "-template", RAW_DATA_INDEX + "*",
                new DocType("cpu", "persistency/rawJvmMonitor.json", JvmMonitorData.class)
        );
    }

    @Override
    public String indexName() {
        // TODO: implement time suffix/alias
        return RAW_DATA_INDEX;
    }


}
