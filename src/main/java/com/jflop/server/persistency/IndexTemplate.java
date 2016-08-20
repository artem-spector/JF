package com.jflop.server.persistency;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base class for index templates.
 * Automatically registers itself on load.
 *
 * @author artem
 *         Date: 8/13/16
 */
public abstract class IndexTemplate implements InitializingBean {

    private String templateName;
    private String template;
    private DocType[] docTypes;

    @Autowired
    protected ESClient esClient;

    protected IndexTemplate(String templateName, String template, DocType... docTypes) {
        this.templateName = templateName;
        this.template = template;
        this.docTypes = docTypes;
    }

    @Override
    public void afterPropertiesSet() {
        esClient.putTemplate(templateName, template, docTypes);
    }

    public <T> PersistentData<T> getDocument(PersistentData<T> doc, Class<T> type) {
        return esClient.getDocument(indexName(), getDocType(type), doc, type);
    }

    public <T> PersistentData<T> createDocument(PersistentData<T> doc) {
        return esClient.createDocument(indexName(), getDocType(doc.source.getClass()), doc);
    }

    protected String getDocType(Class type) {
        for (DocType docType : docTypes) {
            if (docType.type == type) return docType.docType;
        }
        throw new IllegalArgumentException("Unsupported source type: " + type.getName());
    }

    public abstract String indexName();
}
