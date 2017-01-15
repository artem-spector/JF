package com.jflop.server.runtime;

import com.jflop.server.persistency.DocType;
import com.jflop.server.persistency.IndexTemplate;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.runtime.data.processed.FlowSummary;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TODO: Document!
 *
 * @author artem on 08/01/2017.
 */
@Component
public class ProcessedDataIndex extends IndexTemplate {

    public static final String PROCESSED_DATA_INDEX = "jf-processed-data";

    protected ProcessedDataIndex() {
        super(PROCESSED_DATA_INDEX + "-template", PROCESSED_DATA_INDEX + "*",
                new DocType("flowSummary", "persistency/flowSummary.json", FlowSummary.class)
        );
    }

    @Override
    public String indexName() {
        return PROCESSED_DATA_INDEX;
    }

    public void addFlowSummary(FlowSummary data) {
        createDocument(new PersistentData<Object>(data));
    }

    public FlowSummary getLastSummary() {
        List<PersistentData<FlowSummary>> found = find(QueryBuilders.matchAllQuery(), 1, FlowSummary.class, SortBuilders.fieldSort("time").order(SortOrder.DESC));
        if (found != null && found.size() == 1)
            return found.get(0).source;
        else
            return null;
    }
}
