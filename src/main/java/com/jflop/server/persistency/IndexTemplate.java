package com.jflop.server.persistency;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for index templates.
 * Automatically registers itself on load.
 *
 * @author artem
 *         Date: 8/13/16
 */
public abstract class IndexTemplate implements InitializingBean {

    private String name;
    private String template;
    private Map<String, String> docTypes;

    @Autowired
    private ESClient esClient;

    protected IndexTemplate(String name, String template, String... typeMappingPairs) throws IOException {
        assert typeMappingPairs.length % 2 == 0;

        this.name = name;
        this.template = template;
        docTypes = new HashMap<>();

        boolean even = true;
        String docType = null;
        for (String value : typeMappingPairs) {
            if (even) {
                docType = value;
            } else {
                docTypes.put(docType, readMapping(value));
            }
            even = !even;
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        esClient.putTemplate(name, template, docTypes);
    }

    private String readMapping(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        BufferedReader in = new BufferedReader(new InputStreamReader(resource.getInputStream()));
        String contentStr = "";
        String line;
        while ((line = in.readLine()) != null) {
            contentStr += line;
        }
        return contentStr;
    }
}
