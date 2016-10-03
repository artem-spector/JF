package com.jflop.server.persistency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.ServerApp;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.junit.Assert.*;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 8/13/16
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ServerApp.class)
@WebAppConfiguration
public class ESClientTest {

    @Autowired
    private ESClient esClient;

    @Before
    public void cleanup() {
        esClient.deleteIndices("test*");
        esClient.deleteTemplates("test*");
    }

    @Test
    public void testCreateDeleteIndex() {
        String index = "test-index";
        assertFalse(esClient.indexExists(index));

        String id = esClient.createDocument(index, "any", new SomePojo("a", "b"), null);
        assertNotNull(id);

        assertTrue(esClient.indexExists(index));
        esClient.deleteIndices(index);
        assertFalse(esClient.indexExists(index));
    }

    @Test
    public void testCreateDeleteTemplate() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String index = "test-idx-1";
        String docType = "somepojo";

        // the mapping in the jso file does extra-wrapping according to https://github.com/elastic/elasticsearch/issues/17886
        String mappingStr = jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject("name")
                .field("type", "string")
                .field("index", "not_analyzed")
                .endObject()
                .startObject("description")
                .field("type", "string")
                .field("index", "not_analyzed")
                .endObject()
                .endObject()
                .endObject()
                .string();

        MappingMetaData metaData;

        // auto-create the index and make sure the mapping is not as we want
        assertFalse(esClient.indexExists(index));
        esClient.createDocument(index, docType, new SomePojo("aaa", "AAA"), null);
        assertTrue(esClient.indexExists(index));
        metaData = esClient.getMappings(index).get(docType);
        assertNotEquals(mapper.readValue(mappingStr, Map.class), metaData.getSourceAsMap());

        // create the template
        String template = "test-template";
        esClient.deleteIndices("test*");
        assertFalse(esClient.indexExists(index));
        assertEquals(0, esClient.getTemplates("test*").size());
        esClient.putTemplate(template, "test-idx*", new DocType[]{new DocType("somepojo", "persistency/somePojo.json", SomePojo.class)});
        assertEquals(1, esClient.getTemplates(template).size());

        // add document and make sure the mapping is same as in the template
        esClient.createDocument(index, docType, new SomePojo("aaa", "AAA"), null);
        assertTrue(esClient.indexExists(index));
        metaData = esClient.getMappings(index).get(docType);
        assertEquals(mapper.readValue(mappingStr, Map.class), metaData.getSourceAsMap());
    }

    @Test
    public void testIndexTemplate() {
        SomePojoIndex pojoIndex = new SomePojoIndex(esClient);
        pojoIndex.afterPropertiesSet();
        assertFalse(esClient.indexExists(pojoIndex.indexName()));

        SomePojo original = new SomePojo("a", "b");
        PersistentData<SomePojo> res = pojoIndex.createDocument(new PersistentData<>(original));
        assertNotNull(res.id);
        assertEquals(1, res.version);

        assertNull(pojoIndex.getDocument(new PersistentData<>("11", 0), SomePojo.class));

        PersistentData<SomePojo> found = pojoIndex.getDocument(res, SomePojo.class);
        assertEquals(res.id, found.id);
        assertEquals(res.version, found.version);
        assertEquals(res.source, original);
    }

}
