package com.jflop.server.rest.persistency;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 08/07/2017
 */
public class ESClientTest {

    @Test
    public void testConnect() throws Exception {
        ESClient esClient = new ESClient("localhost", 9300, "jf-server", null);
        assertNotNull(esClient);
        boolean indexExists = esClient.indexExists("not_existing");
        assertFalse(indexExists);
        esClient.destroy();
    }
}
