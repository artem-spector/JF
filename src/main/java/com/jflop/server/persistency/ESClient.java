package com.jflop.server.persistency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesResponse;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequestBuilder;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.metadata.IndexTemplateMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

/**
 * Elastic search client
 *
 * @author artem
 *         Date: 8/13/16
 */

@Component
public class ESClient implements InitializingBean, DisposableBean {

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
        client.admin().indices().prepareDelete(names).get();
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
            return new PersistentData<T>(response.getId(), response.getVersion(), doc.source);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> PersistentData<T> getDocument(String indexName, String docType, PersistentData<T> data, Class<T> type) {
        GetRequestBuilder request = client.prepareGet(indexName, docType, data.id);
        if (data.version != 0) request.setVersion(data.version);
        GetResponse response = request.execute().actionGet();
        if (response.isExists())
            try {
                return new PersistentData<>(response.getId(), response.getVersion(), mapper.readValue(response.getSourceAsBytes(), type));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        else
            return null;
    }
}
