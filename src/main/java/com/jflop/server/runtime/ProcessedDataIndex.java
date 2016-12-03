package com.jflop.server.runtime;

import com.jflop.server.persistency.DocType;
import com.jflop.server.persistency.IndexTemplate;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.runtime.data.ThreadDumpMetadata;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Index for the data that is created/modified by server itself.
 *
 * @author artem
 *         Date: 11/26/16
 */
@Component
public class ProcessedDataIndex extends IndexTemplate {

    private static final String PROCESSED_DATA_INDEX = "jf-processed-data";

    public ProcessedDataIndex() {
        super(PROCESSED_DATA_INDEX + "-template", PROCESSED_DATA_INDEX + "*",
                new DocType("threadDump", "persistency/threadDumpMetadata.json", ThreadDumpMetadata.class));
    }

    @Override
    public String indexName() {
        return PROCESSED_DATA_INDEX;
    }

    public List<ThreadDumpMetadata> getThreadDumps(String accountId) {
        TermQueryBuilder query = QueryBuilders.termQuery("accountId", accountId);
        List<PersistentData<ThreadDumpMetadata>> found = find(query, 10000, ThreadDumpMetadata.class);

        List<ThreadDumpMetadata> res = new ArrayList<>();
        for (PersistentData<ThreadDumpMetadata> persistentData : found) {
            res.add(persistentData.source);
        }

        return res;
    }

    public void addThreadDump(ThreadDumpMetadata threadDumpMetadata) {
        createDocument(new PersistentData<Object>(threadDumpMetadata.dumpId, 0, threadDumpMetadata));
    }
}
