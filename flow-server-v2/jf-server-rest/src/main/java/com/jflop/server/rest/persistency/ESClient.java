package com.jflop.server.rest.persistency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesResponse;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequestBuilder;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.get.*;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.cluster.metadata.IndexTemplateMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.logging.Logger;

/**
 * Elastic search client
 *
 * @author artem
 *         Date: 8/13/16
 */

@Component
public class ESClient implements InitializingBean, DisposableBean {

    private static final Logger logger = Logger.getLogger(ESClient.class.getName());

    private ObjectMapper mapper = new ObjectMapper();

    @Value("${elasticsearch.host}")
    private String esHost;

    @Value("${elasticsearch.port}")
    private int esPort;

    @Value("${cluster.name}")
    private String esCluster;

    private TransportClient client;

    public ESClient() {
    }

    public ESClient(String esHost, int esPort, String cluster, String credentials) throws Exception {
        this.esHost = esHost;
        this.esPort = esPort;
        this.esCluster = cluster;
        afterPropertiesSet();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Settings settings = Settings.builder()
                .put("cluster.name", esCluster)
                .build();
        client = new PreBuiltTransportClient(settings)
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(esHost), esPort));
    }

    @Override
    public void destroy() throws Exception {
        client.close();
    }

    public List<IndexTemplateMetaData> getTemplates(String templates) {
        GetIndexTemplatesResponse response = client.admin().indices().prepareGetTemplates(templates).execute().actionGet();
        return response.getIndexTemplates();
    }

    public void putTemplate(String name, String template, Collection<DocType> docTypes) {
        PutIndexTemplateRequestBuilder request = client.admin().indices().preparePutTemplate(name).setTemplate(template).setOrder(1);
        for (DocType docType : docTypes) {
            request.addMapping(docType.docType, docType.readMapping(), XContentType.JSON);
        }
        request.execute().actionGet();
        awaitClusterAvailable(5);
    }

    public void deleteTemplates(String... names) {
        List<IndexTemplateMetaData> templates = client.admin().indices().prepareGetTemplates(names).execute().actionGet().getIndexTemplates();
        for (IndexTemplateMetaData template : templates) {
            client.admin().indices().prepareDeleteTemplate(template.getName()).execute().actionGet();
        }
        awaitClusterAvailable(5);
    }

    public void deleteIndices(String... names) {
        try {
            client.admin().indices().prepareDelete(names).get();
        } catch (IndexNotFoundException e) {
            logger.warning("Failed to delete " + Arrays.toString(names) + ": " + e);
        }
        awaitClusterAvailable(5);
    }

    public boolean indexExists(String... names) {
        return client.admin().indices().prepareExists(names).execute().actionGet().isExists();
    }

    public ImmutableOpenMap<String, MappingMetaData> getMappings(String index) {
        GetMappingsResponse response = client.admin().indices().prepareGetMappings(index).execute().actionGet();
        return response.getMappings().get(index);
    }

    public String createDocument(String index, String docType, Object pojo, String id) {
        try {
            IndexRequestBuilder request = client.prepareIndex(index, docType).setSource(mapper.writeValueAsBytes(pojo), XContentType.JSON);
            if (id != null) {
                request.setId(id);
                request.setCreate(true);
            }
            return request.execute().actionGet().getId();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> PersistentData<T> createDocument(String index, String docType, PersistentData<T> doc) {
        try {
            IndexRequestBuilder request = client.prepareIndex(index, docType).setSource(mapper.writeValueAsBytes(doc.source), XContentType.JSON);
            if (doc.id != null) {
                request.setId(doc.id);
                request.setCreate(true);
            }
            IndexResponse response = request.execute().actionGet();
            return new PersistentData<>(response.getId(), response.getVersion(), doc.source);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Index the given document if it does not exist yet.
     * If already exists - do nothing, and return the existing document.
     *
     * @param <T>     the source type
     * @param index   index name
     * @param docType doctype
     * @param doc     the document (id and source)
     * @param type    the source class
     * @return null if the document was created, or the existing document otherwise
     */
    public <T> PersistentData<T> createDocumentIfNotExists(String index, String docType, PersistentData<T> doc, Class<T> type) {
        try {
            return createDocument(index, docType, doc);
        } catch (VersionConflictEngineException e) {
            return getDocument(index, docType, doc, type);
        }
    }

    public <T> PersistentData<T> updateDocument(String index, String docType, PersistentData<T> doc) {
        try {
            UpdateRequestBuilder request = client.prepareUpdate(index, docType, doc.id).setDoc(mapper.writeValueAsBytes(doc.source), XContentType.JSON);
            if (doc.version != 0) request.setVersion(doc.version);
            UpdateResponse response = request.execute().actionGet();
            return new PersistentData<>(response.getId(), response.getVersion(), doc.source);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> PersistentData<T> getDocument(String indexName, String docType, PersistentData<T> data, Class<T> type) {
        GetRequestBuilder request = client.prepareGet(indexName, docType, data.id);
        if (data.version != 0) request.setVersion(data.version);

        GetResponse response;
        try {
            response = request.execute().actionGet();
        } catch (IndexNotFoundException e) {
            return null;
        }

        if (response.isExists())
            try {
                return new PersistentData<>(response.getId(), response.getVersion(), mapper.readValue(response.getSourceAsBytes(), type));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        else
            return null;
    }

    public <T> List<T> getDocuments(String indexName, String docType, Class<T> type, Set<String> ids) {
        MultiGetRequestBuilder request = client.prepareMultiGet().add(indexName, docType, ids);
        MultiGetResponse response;
        try {
            response = request.execute().actionGet();
        } catch (IndexNotFoundException e) {
            return null;
        }

        List<T> res = new ArrayList<>();
        for (Iterator<MultiGetItemResponse> iterator = response.iterator(); iterator.hasNext(); ) {
            MultiGetItemResponse item = iterator.next();
            GetResponse itemResponse = item.getResponse();
            if (!item.isFailed() && itemResponse.isExists()) {
                try {
                    res.add(mapper.readValue(itemResponse.getSourceAsBytes(), type));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return res;
    }

    public boolean deleteDocument(String indexName, String docType, PersistentData document) {
        DeleteRequestBuilder request = client.prepareDelete(indexName, docType, document.id);
        if (document.version != 0) request.setVersion(document.version);
        return request.execute().actionGet().status() == RestStatus.FOUND;
    }

    public SearchResponse search(String indexName, String type, QueryBuilder query, int maxHits, SortBuilder sort) {
        SearchRequestBuilder searchQuery = client.prepareSearch(indexName).setTypes(type).setQuery(query).setSize(maxHits).setVersion(true);
        if (sort != null)
            searchQuery.addSort(sort);
        try {
            return searchQuery.execute().actionGet();
        } catch (IndexNotFoundException e) {
            return null;
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public Aggregation aggregate(String indexName, String type, QueryBuilder query, AbstractAggregationBuilder aggregation) {
        SearchRequestBuilder searchQuery = client.prepareSearch(indexName).setTypes(type).setQuery(query).setSize(0)
                .addAggregation(aggregation);
        try {
            SearchResponse response = searchQuery.execute().actionGet();
            return response.getAggregations().get(aggregation.getName());
        } catch (IndexNotFoundException e) {
            return null;
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public void deleteByQuery(String indexName, QueryBuilder query) {
        int maxBulkLen = 100;
        SearchRequestBuilder searchQuery = client.prepareSearch(indexName).setQuery(query);

        boolean done = false;
        while (!done) {
            searchQuery.setSize(maxBulkLen);
            SearchResponse response = searchQuery.execute().actionGet();
            SearchHit[] hits = response.getHits().hits();
            done = response.getHits().totalHits() == hits.length;

            BulkRequestBuilder bulk = client.prepareBulk();
            for (SearchHit hit : hits) {
                bulk.add(client.prepareDelete().setIndex(indexName).setType(hit.getType()).setId(hit.id()));
            }
            try {
                bulk.execute().get();
            } catch (Exception e) {
                logger.severe("Bulk delete failed: " + e);
            }
        }
    }

    public void refreshIndices(String indexName) {
        try {
            client.admin().indices().prepareRefresh(indexName).execute().actionGet();
        } catch (IndexNotFoundException e) {
            logger.warning("Failed to refresh index. " + e);
        }
    }

    public void awaitClusterAvailable(int timeoutSec) {
        long timeputMillis = System.currentTimeMillis() + timeoutSec * 1000;
        ClusterHealthStatus status = null;
        do {
            try {
                status = client.admin().cluster().prepareHealth().execute().get().getStatus();
                if (status == ClusterHealthStatus.GREEN || status == ClusterHealthStatus.YELLOW) return;
                Thread.sleep(300);
            } catch (Exception e) {
                // ignore
            }
        } while (System.currentTimeMillis() < timeputMillis);

        throw new RuntimeException("Cluster status is " + status + " after " + timeoutSec + " sec");
    }

}
