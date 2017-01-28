package com.jflop.server.runtime.data;

import com.jflop.load.GeneratedFlow;
import com.jflop.server.background.JvmMonitorAnalysis;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO: Document!
 *
 * @author artem on 19/01/2017.
 */
public class SampleTest {

    private static final String[] FOLDERS = new String[]{
            "samples/analysisSteps/1/",
            "samples/analysisSteps/2/"
    };

    @Test
    public void testStatistics() throws IOException {
        for (String folderPath : FOLDERS) {
            for (AnalysisStepTestHelper helper : steps(folderPath)) {
                helper.checkFlowStatistics(null, 0.5f);
            }
        }
    }

    @Test
    public void testSameFlows() throws IOException {
        for (String folderPath : FOLDERS) {
            int maxSize = 0;
            int count = 1;
            for (AnalysisStepTestHelper helper : steps(folderPath)) {
                for (Set<String> ids : helper.groupSameFlows()) {
                    maxSize = Math.max(maxSize, ids.size());
                    if (ids.size() > 1)
                        System.out.println("step " + count + " same flows: " + ids);
                }
                count++;
            }
            assertEquals("Unexpected same flows", 1, maxSize);
        }
    }

    @Test
    public void testMapThreadsToFlows() throws IOException {
        for (String folderPath : FOLDERS) {
            for (AnalysisStepTestHelper helper : steps(folderPath)) {
                helper.checkThreadsCoverage();
            }
        }
    }

    @Test
    public void testGroupFlowSummary() throws IOException {
        for (String folderPath : FOLDERS) {
            for (AnalysisStepTestHelper helper : steps(folderPath)) {
                helper.calculateDistanceAndOutline();
            }
        }
    }

    @Test
    public void testCreateMetrics() throws IOException {
        for (String folderPath : FOLDERS) {
            for (AnalysisStepTestHelper helper : steps(folderPath)) {
                Map<String, Float> line = helper.createMetrics();

                System.out.println("metric line:");
                for (Map.Entry<String, Float> entry : line.entrySet()) {
                    System.out.println(entry.getKey() + "->" + entry.getValue());
                }
            }
        }
    }

    private Iterable<AnalysisStepTestHelper> steps(String folderPath) throws IOException {
        Iterator<AnalysisStepTestHelper> iterator = new Iterator<AnalysisStepTestHelper>() {

            private Object[][] flowsAndThroughput = GeneratedFlow.read(getClasspathFile(folderPath + "generatedFlows.json"));
            private File[] stepFiles = getFilesInFolder(folderPath, "step", ".json");
            private int pos = 0;

            @Override
            public boolean hasNext() {
                return pos <= stepFiles.length - 1;
            }

            @Override
            public AnalysisStepTestHelper next() {
                try {
                    System.out.println("--------- step " + (pos + 1) + " -----------");
                    return new AnalysisStepTestHelper(JvmMonitorAnalysis.StepState.readFromFile(stepFiles[pos++]), flowsAndThroughput);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return () -> iterator;
    }

    private File[] getFilesInFolder(String folderPath, String prefix, String suffix) throws IOException {
        ClassPathResource rsc = new ClassPathResource(folderPath);
        assertTrue("File does not exist: " + rsc.getURL(), rsc.exists());
        File folder = new File(rsc.getURL().getPath());
        assertTrue("Not a folder: " + folder.getAbsolutePath(), folder.isDirectory());

        return folder.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(suffix));
    }

    private File getClasspathFile(String path) throws IOException {
        ClassPathResource rsc = new ClassPathResource(path);
        assertTrue("File does not exist: " + rsc.getURL(), rsc.exists());
        return rsc.getFile();
    }
}
