package com.jflop.server.runtime;

import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.persistency.DocType;
import com.jflop.server.persistency.IndexTemplate;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.runtime.data.Metadata;
import com.jflop.server.runtime.data.ThreadMetadata;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Index for metadata like stacktraces or flows definitions that are share by many instances of the raw data,
 * and kept for long time
 *
 * @author artem
 *         Date: 11/26/16
 */
@Component
public class MetadataIndex extends IndexTemplate {

    private static final String METADADATA_INDEX = "jf-metadata";

    public MetadataIndex() {
        super(METADADATA_INDEX + "-template", METADADATA_INDEX + "*",
                new DocType("thread", "persistency/threadMetadata.json", ThreadMetadata.class)
        );
    }

    @Override
    public String indexName() {
        return METADADATA_INDEX;
    }

    public void addMetadata(List<Metadata> list) {
        for (Metadata metadata : list) {
            createDocumentIfNotExists(new PersistentData<>(metadata.getDocumentId(), 0, metadata));
        }
    }

    public <T extends Metadata> List<T> getMetadata(AgentJVM agentJVM, Class<T> metadataClass, Date fromTime, int maxHits) {
        QueryBuilder query = QueryBuilders.boolQuery()
                .must(QueryBuilders.rangeQuery("time").gte(fromTime))
                .must(QueryBuilders.termQuery("agentJvm.accountId", agentJVM.accountId))
                .must(QueryBuilders.termQuery("agentJvm.agentId", agentJVM.agentId))
                .must(QueryBuilders.termQuery("agentJvm.jvmId", agentJVM.jvmId));

        List<PersistentData<T>> found = find(query, maxHits, metadataClass);

        List<T> res = new ArrayList<T>();
        for (PersistentData<T> doc : found) {
            res.add(doc.source);
        }
        return res;
    }
}
