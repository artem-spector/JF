package com.jflop.server.persistency;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 8/20/16
 */
public class SomePojoIndex extends IndexTemplate {

    private static final String INDEX = "test-somepojo-one";
    private static final String DOCTYPE = "somepojo";

    public SomePojoIndex(ESClient client) {
        super("test-template", "test-somepojo*", new DocType(DOCTYPE, "persistency/somePojo.json", SomePojo.class));
        this.esClient = client;
    }

    @Override
    public String indexName() {
        return INDEX;
    }
}
