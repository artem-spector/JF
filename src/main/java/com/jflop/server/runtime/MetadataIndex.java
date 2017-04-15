package com.jflop.server.runtime;

import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.persistency.DocType;
import com.jflop.server.persistency.IndexTemplate;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.runtime.data.*;
import com.jflop.server.runtime.data.metric.MetricMetadata;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Index for metadata like stacktraces or flow definitions that are shared by many instances of raw data,
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
                new DocType("class", "persistency/instrumentationMetadata.json", InstrumentationMetadata.class),
                new DocType("flow", "persistency/flowMetadata.json", FlowMetadata.class),
                new DocType("metric", "persistency/metricMetadata.json", MetricMetadata.class)
        );
    }

    @Override
    public String indexName() {
        return METADADATA_INDEX;
    }

    public void addMetadata(List<Metadata> list) {
        for (Metadata metadata : list) {
            PersistentData<Metadata> doc = new PersistentData<>(metadata.getDocumentId(), 0, metadata);
            PersistentData<Metadata> existing = createDocumentIfNotExists(doc);
            if (existing != null && metadata.mergeTo(existing.source))
                updateDocument(existing);
        }
    }

    public <T extends Metadata> List<T> findMetadata(AgentJVM agentJVM, Class<T> metadataClass, Date fromTime, int maxHits) {
        QueryBuilder query = QueryBuilders.boolQuery()
                .must(QueryBuilders.rangeQuery("time").gte(fromTime.getTime()))
                .must(QueryBuilders.termQuery("agentJvm.accountId", agentJVM.accountId))
                .must(QueryBuilders.termQuery("agentJvm.agentId", agentJVM.agentId))
                .must(QueryBuilders.termQuery("agentJvm.jvmId", agentJVM.jvmId));

        List<PersistentData<T>> found = find(query, maxHits, metadataClass, null);

        return found.stream().map(doc -> doc.source).collect(Collectors.toList());
    }

    public InstrumentationMetadata getClassMetadata(AgentJVM agentJVM, String className) {
        String id = new InstrumentationMetadata(agentJVM, className).getDocumentId();
        PersistentData<InstrumentationMetadata> document = getDocument(new PersistentData<>(id, 0), InstrumentationMetadata.class);
        return document == null ? null : document.source;
    }

    public Set<String> getBlacklistedClasses(AgentJVM agentJVM) {
        QueryBuilder query = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery("isBlacklisted", true))
                .must(QueryBuilders.termQuery("agentJvm.accountId", agentJVM.accountId))
                .must(QueryBuilders.termQuery("agentJvm.agentId", agentJVM.agentId))
                .must(QueryBuilders.termQuery("agentJvm.jvmId", agentJVM.jvmId));

        List<PersistentData<InstrumentationMetadata>> found = find(query, 10000, InstrumentationMetadata.class, null);

        return found.stream().map(doc -> doc.source.className).collect(Collectors.toSet());
    }

    public PersistentData<MetricMetadata> getOrCreateMetricMetadata(AgentDataFactory dataFactory) {
        String id = MetricMetadata.getMetadataId(dataFactory.getAgentJVM());
        PersistentData<MetricMetadata> doc = getDocument(new PersistentData<>(id, 0), MetricMetadata.class);
        if (doc == null) {
            MetricMetadata metricMetadata = dataFactory.createInstance(MetricMetadata.class);
            doc = createDocument(new PersistentData<>(id, 0, metricMetadata));
        }
        return doc;
    }
}
