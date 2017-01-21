package com.jflop.server.runtime.data;

import com.jflop.TestUtil;
import com.jflop.load.GeneratedFlow;
import com.jflop.server.runtime.data.processed.FlowSummary;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * TODO: Document!
 *
 * @author artem on 19/01/2017.
 */
public class SampleTest {

    @Test
    public void testSameFlows() throws IOException {
        assertEquals(2, countSame("samples/sameFlows/1/", "flow1.json", "flow2.json"));
        assertEquals(1, countSame("samples/sameFlows/2/", "flow1.json", "flow2.json"));
    }

    @Test
    public void testFindFlowInSummary() throws IOException {
        findFlowInSummary("samples/flowSummary/1/");
        findFlowInSummary("samples/flowSummary/2/");
        findFlowInSummary("samples/flowSummary/3/");
        findFlowInSummary("samples/flowSummary/4/");
        findFlowInSummary("samples/flowSummary/5/");
    }

    private int countSame(String folderPath, String... flowFiles) throws IOException {
        Map<String, FlowMetadata> allFlows = new HashMap<>();
        for (String flowFile : flowFiles) {
            FlowMetadata metadata = readFromFile(folderPath + flowFile, FlowMetadata.class);
            allFlows.put(metadata.getDocumentId(), metadata);
        }

        Set<String> same = new HashSet<>();
        for (String flowId : allFlows.keySet()) {
            boolean isSame = true;
            for (String sameFlowId : same) {
                if (!FlowMetadata.maybeSame(allFlows.get(flowId), allFlows.get(sameFlowId))) {
                    System.out.println(flowId + " cannot not be same as " + sameFlowId);
                    isSame = false;
                    break;
                }
            }
            if (isSame) same.add(flowId);
        }

        return same.size();
    }

    private void findFlowInSummary(String folderPath) throws IOException {
        FlowSummary summary = readFromFile(folderPath + "summary1.json", FlowSummary.class);

        Map<String, FlowMetadata> flows = new HashMap<>();
        for (File file : getFilesInFolder(folderPath, "flow", ".json")) {
            FlowMetadata flowMetadata = readFromFile(folderPath + file.getName(), FlowMetadata.class);
            flows.put(flowMetadata.getDocumentId(), flowMetadata);
        }

        String generatedFlowFile = new ClassPathResource(folderPath + "generatedFlow1.json").getURL().getPath();
        GeneratedFlow generatedFlow = GeneratedFlow.fromString(TestUtil.readStringFromFile(generatedFlowFile));

        Set<String> found = generatedFlow.findFlowIds(summary, flows);
        assertNotNull(found);
        assertFalse(found.isEmpty());

        if (found.size() > 1) {
            Set<String> same = new HashSet<>();
            for (String flowId : found) {
                if (!same.isEmpty())
                    for (String sameFlowId : same) {
                        boolean maybeSame = FlowMetadata.maybeSame(flows.get(flowId), flows.get(sameFlowId));
                        assertTrue("Flows " + flowId + " and " + sameFlowId + " cannot represent same flow", maybeSame);
                    }
                same.add(flowId);
            }
        }
        System.out.println("Flow found: " + found);
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
