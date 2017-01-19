package com.jflop.server.runtime.data;

import com.jflop.TestUtil;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * TODO: Document!
 *
 * @author artem on 19/01/2017.
 */
public class FlowMetadataTest {

    @Test
    public void testSameFlows() throws IOException {
        FlowMetadata flow1 = readFlow("samples/sameFlows/1/flow1.json");
        assertTrue(flow1.representsSameFlowAs(flow1));

        FlowMetadata flow2 = readFlow("samples/sameFlows/1/flow2.json");
        assertTrue(flow2.representsSameFlowAs(flow2));

        assertTrue(flow1.representsSameFlowAs(flow2));
        assertTrue(flow2.representsSameFlowAs(flow1));
    }

    private FlowMetadata readFlow(String path) throws IOException {
        ClassPathResource rsc = new ClassPathResource(path);
        assertTrue("File does not exist: " + rsc.getURL(), rsc.exists());
        return TestUtil.readFromFile(rsc.getURL().getPath(), FlowMetadata.class);
    }
}
