package com.jflop.server.rest.persistency;

import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Document type consists of name, mapping json file, and the source doc class
 *
 * @author artem
 *         Date: 8/20/16
 */
public class DocType {

    public final String docType;
    private final String mappingFile;
    public final Class type;

    public DocType(String docType, String mappingFile, Class type) {
        this.docType = docType;
        this.mappingFile = mappingFile;
        this.type = type;
    }

    public String readMapping() {
        try {
            ClassPathResource resource = new ClassPathResource(mappingFile);
            BufferedReader in = new BufferedReader(new InputStreamReader(resource.getInputStream()));
            String contentStr = "";
            String line;
            while ((line = in.readLine()) != null) {
                contentStr += line;
            }
            return contentStr;
        } catch (IOException e) {
            throw new RuntimeException("Failed reading mapping from " + mappingFile, e);
        }
    }

}
