package com.jflop.server.persistency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Base class for index templates.
 * Automatically registers itself on load.
 *
 * @author artem
 *         Date: 8/13/16
 */
public abstract class IndexTemplate implements InitializingBean {

    private static final Logger logger = Logger.getLogger(IndexTemplate.class.getName());

    private ObjectMapper mapper = new ObjectMapper();

    private String templateName;
    private String template;
    private Map<Class, DocType> docTypes;

    @Autowired
    protected ESClient esClient;

    protected IndexTemplate(String templateName, String template, DocType... docTypes) {
        this.templateName = templateName;
        this.template = template;
        this.docTypes = new HashMap<>();
        for (DocType docType : docTypes) this.docTypes.put(docType.type, docType);
    }

    @Override
    public void afterPropertiesSet() {
        esClient.putTemplate(templateName, template, docTypes.values());
    }

    public <T> PersistentData<T> getDocument(PersistentData<T> doc, Class<T> type) {
        return esClient.getDocument(indexName(), getDocType(type), doc, type);
    }

    public void deleteIndex() {
        esClient.deleteIndices(indexName());
    }

    public void refreshIndex() {
        esClient.refreshIndices(indexName());
    }

    public <T> PersistentData<T> createDocument(PersistentData<T> doc) {
        return esClient.createDocument(indexName(), getDocType(doc.source.getClass()), doc);
    }

    public <T> PersistentData<T> createDocumentIfNotExists(PersistentData<T> doc) {
        return esClient.createDocumentIfNotExists(indexName(), getDocType(doc.source.getClass()), doc);
    }

    public boolean deleteDocument(PersistentData doc) {
        return esClient.deleteDocument(indexName(), getDocType(doc.source.getClass()), doc);
    }

    public <T> PersistentData<T> updateDocument(PersistentData<T> doc) {
        return esClient.updateDocument(indexName(), getDocType(doc.source.getClass()), doc);
    }

    public void deleteByQuery(QueryBuilder query) {
        esClient.deleteByQuery(indexName(), query);
    }

    public <T> List<PersistentData<T>> find(QueryBuilder query, int maxHits, Class<T> dataType) {
        List<PersistentData<T>> res = new ArrayList<>();

        SearchResponse response = esClient.search(indexName(), getDocType(dataType), query, maxHits);
        if (response != null) {
            for (SearchHit hit : response.getHits().getHits()) {
                try {
                    res.add(new PersistentData<>(hit.id(), hit.version(), mapper.readValue(hit.source(), dataType)));
                } catch (IOException e) {
                    logger.warning("Failed to read persistent data as " + dataType.getName() + ": " + e);
                }
            }
        }

        return res;
    }

    public <T> PersistentData<T> findSingle(QueryBuilder query, Class<T> dataType) {
        List<PersistentData<T>> found = find(query, 2, dataType);
        int size = found.size();
        if (size == 0)
            return null;
        else if (size == 1)
            return found.get(0);
        else
            throw new RuntimeException("Found " + size + " elements when maximum one expected.");
    }

    public Collection<? extends DocType> getDocTypes() {
        return docTypes.values();
    }

    public String getDocType(Class type) {
        DocType docType = docTypes.get(type);
        if (docType != null) return docType.docType;
        throw new IllegalArgumentException("Unsupported source type: " + type.getName());
    }

    public abstract String indexName();
}
