package com.jflop.server.runtime;

import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.persistency.DocType;
import com.jflop.server.persistency.IndexTemplate;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.persistency.ValuePair;
import com.jflop.server.runtime.data.InstrumentationMetadata;
import com.jflop.server.runtime.data.Metadata;
import com.jflop.server.runtime.data.ThreadMetadata;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Component;

import java.util.*;

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
                new DocType("thread", "persistency/threadMetadata.json", ThreadMetadata.class),
                new DocType("class", "persistency/instrumentationMetadata.json", InstrumentationMetadata.class)
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

    public Set<ValuePair<String, String>> getInstrumentableMethods(Set<String> dumpIds) {
        Set<ValuePair<String, String>> res = new HashSet<>();
        BoolQueryBuilder dumps = null;
        int termCount = 0;
        int maxTerms = 1024;

        QueryBuilder instrumentable = QueryBuilders.termQuery("instrumentable", true);

        for(Iterator<String> iterator = dumpIds.iterator(); iterator.hasNext();) {
            if (dumps == null) dumps = QueryBuilders.boolQuery();
            dumps = dumps.should(QueryBuilders.termQuery("dumpId", iterator.next()));
            termCount++;

            if (termCount == maxTerms || !iterator.hasNext()) {
                BoolQueryBuilder query = QueryBuilders.boolQuery().must(instrumentable).must(dumps);
                List<PersistentData<ThreadMetadata>> found = find(query, termCount, ThreadMetadata.class);
                for (PersistentData<ThreadMetadata> data : found) {
                    res.addAll(data.source.getInstrumentableMethods());
                }
                dumps = null;
                termCount = 0;
            }
        }

        return res;
    }

    public InstrumentationMetadata getClassMetadata(AgentJVM agentJVM, String className) {
        String id = new InstrumentationMetadata(agentJVM, className).getDocumentId();
        PersistentData<InstrumentationMetadata> document = getDocument(new PersistentData<>(id, 0), InstrumentationMetadata.class);
        return document == null ? null : document.source;
    }
}
