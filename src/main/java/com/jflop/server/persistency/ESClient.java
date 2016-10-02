package com.jflop.server.persistency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesResponse;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequestBuilder;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.metadata.IndexTemplateMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
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

    private Client client;

    @Override
    public void afterPropertiesSet() throws Exception {
        client = TransportClient.builder().build()
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

    public void putTemplate(String name, String template, DocType[] docTypes) {
        PutIndexTemplateRequestBuilder request = client.admin().indices().preparePutTemplate(name).setTemplate(template).setOrder(1);
        for (DocType docType : docTypes) {
            request.addMapping(docType.docType, docType.readMapping());
        }
        request.execute().actionGet();
    }

    public void deleteTemplates(String... names) {
        List<IndexTemplateMetaData> templates = client.admin().indices().prepareGetTemplates(names).execute().actionGet().getIndexTemplates();
        for (IndexTemplateMetaData template : templates) {
            client.admin().indices().prepareDeleteTemplate(template.getName()).execute().actionGet();
        }
    }

    public void deleteIndices(String... names) {
        try {
            client.admin().indices().prepareDelete(names).get();
        } catch (IndexNotFoundException e) {
            logger.warning("Failed to delete " + Arrays.toString(names) + ". " + e);
        }
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
            IndexRequestBuilder request = client.prepareIndex(index, docType).setCreate(true).setSource(mapper.writeValueAsBytes(pojo));
            if (id != null) request.setId(id);
            return request.execute().actionGet().getId();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> PersistentData<T> createDocument(String index, String docType, PersistentData<T> doc) {
        try {
            IndexRequestBuilder request = client.prepareIndex(index, docType).setCreate(true).setSource(mapper.writeValueAsBytes(doc.source));
            if (doc.id != null) request.setId(doc.id);
            IndexResponse response = request.execute().actionGet();
            return new PersistentData<>(response.getId(), response.getVersion(), doc.source);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> PersistentData<T> updateDocument(String index, String docType, PersistentData<T> doc) {
        try {
            UpdateRequestBuilder request = client.prepareUpdate(index, docType, doc.id).setDoc(mapper.writeValueAsBytes(doc.source));
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

    public boolean deleteDocument(String indexName, String docType, PersistentData document) {
        DeleteRequestBuilder request = client.prepareDelete(indexName, docType, document.id);
        if (document.version != 0) request.setVersion(document.version);
        return request.execute().actionGet().isFound();
    }

    public SearchResponse search(String indexName, QueryBuilder query, int maxHits) {
        SearchRequestBuilder searchQuery = client.prepareSearch(indexName).setQuery(query).setSize(maxHits).setVersion(true);
        try {
            return searchQuery.execute().actionGet();
        } catch (IndexNotFoundException e) {
            return null;
        }
    }

    public void deleteByQuery(String indexName, QueryBuilder query) {
        int maxBulkLen = 100;
        SearchRequestBuilder searchQuery = client.prepareSearch(indexName).setQuery(query).setNoFields();

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
        client.admin().indices().prepareRefresh(indexName).execute().actionGet();
    }
}
