package com.jflop.server.runtime.data;

import com.jflop.TestUtil;
import com.jflop.load.GeneratedFlow;
import com.jflop.server.runtime.data.processed.FlowSummary;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO: Document!
 *
 * @author artem on 19/01/2017.
 */
public class SampleTest {

    @Test
    public void testSameFlows() throws IOException {
        FlowMetadata flow1 = readFromFile("samples/sameFlows/1/flow1.json", FlowMetadata.class);
        assertTrue(FlowMetadata.maybeSame(flow1, flow1));

        FlowMetadata flow2 = readFromFile("samples/sameFlows/1/flow2.json", FlowMetadata.class);
        assertTrue(FlowMetadata.maybeSame(flow2, flow2));

        assertTrue(FlowMetadata.maybeSame(flow1, flow2));
        assertTrue(FlowMetadata.maybeSame(flow2, flow1));
    }

    @Test
    public void testFindFlowInSummary() throws IOException {
        String folderPath = "samples/flowSummary/1/";
        FlowSummary summary = readFromFile(folderPath + "summary1.json", FlowSummary.class);

        Map<String, FlowMetadata> flows = new HashMap<>();
        for (File file : getFilesInFolder("samples/flowSummary/1", "flow", ".json")) {
            FlowMetadata flowMetadata = readFromFile(folderPath + file.getName(), FlowMetadata.class);
            flows.put(flowMetadata.getDocumentId(), flowMetadata);
        }

        String generatedFlowFile = new ClassPathResource(folderPath + "generatedFlow1.txt").getURL().getPath();
        GeneratedFlow generatedFlow = GeneratedFlow.fromString(TestUtil.readStringFromFile(generatedFlowFile));

        Set<String> found = generatedFlow.findFlowIds_(summary, flows);
        assertNotNull(found);
        assertTrue("Flow not found in summary", found.size() == 1);
    }

    private <T> T readFromFile(String path, Class<T> valueType) throws IOException {
        ClassPathResource rsc = new ClassPathResource(path);
        assertTrue("File does not exist: " + rsc.getURL(), rsc.exists());
        return TestUtil.readValueFromFile(rsc.getURL().getPath(), valueType);
    }

    private File[] getFilesInFolder(String folderPath, String prefix, String suffix) throws IOException {
        ClassPathResource rsc = new ClassPathResource(folderPath);
        assertTrue("File does not exist: " + rsc.getURL(), rsc.exists());
        File folder = new File(rsc.getURL().getPath());
        assertTrue("Not a folder: " + folder.getAbsolutePath(), folder.isDirectory());

        return folder.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(suffix));
    }
}
