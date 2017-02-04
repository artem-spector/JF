package com.jflop.load;

import com.jflop.server.background.JvmMonitorAnalysis;
import com.jflop.server.runtime.ProcessedDataIndex;
import com.jflop.server.runtime.data.AnalysisStepTestHelper;
import com.jflop.server.runtime.data.processed.FlowSummary;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import static org.junit.Assert.*;

/**
 * TODO: Document!
 *
 * @author artem on 15/01/2017.
 */
public class AnalysisTest extends LoadTestBase {

    @Autowired
    private ProcessedDataIndex processedDataIndex;

    @Autowired
    private JvmMonitorAnalysis analysisTask;

    public AnalysisTest() {
        super("AnalysisTest", true);
    }

    @Before
    public void startClient() throws Exception {
        startClient("analysisTestAgent");
    }

    @Test
    public void testSingleFlow() throws Exception {
        generateFlows(1, 20, 20, 100, 100);
        runFlows(flowsAndThroughput, 3, "target/testSingleFlow-temp");
    }

    @Test
    public void testMultipleFlows() throws Exception {
        generateFlows(6, 10, 100, 50, 200);
        runFlows(flowsAndThroughput, 3, "target/testMultipleFlows-temp");
    }

    @Test
    public void runContinuously() throws Exception {
        generateFlows(6, 10, 100, 50, 200);
        runFlows(flowsAndThroughput, 10000, "target/testMultipleFlows-temp");
    }

    private void runFlows(Object[][] generatedFlows, int numIterations, String folderPath) throws Exception {
        startLoad();
        startMonitoring();
        awaitNextSummary(30, null); // skip the first summary

        File folder = null;
        if (folderPath != null) {
            folder = prepareFolder(folderPath);
            GeneratedFlow.save(new File(folder, "generatedFlows.json"), generatedFlows);
        }

        Map<String, Set<String>> previous = null;
        for (int i = 0; i < numIterations; i++) {
            String fileName = "step" + (i + 1) + ".json";
            if (folderPath != null) {
                File file = new File(folder, fileName);
                analysisTask.saveStepToFile(file);
            }

            System.out.println("-------------- step " + (i + 1) + " ---------------");
            awaitNextSummary(10, new Date());

            if (folderPath != null) {
                LoadRunner.LoadResult loadResult = getLoadResult();
                File file = new File(folder, fileName);
                assertTrue("File does not exist: " + file.getAbsolutePath(), file.exists());
                AnalysisStepTestHelper helper = new AnalysisStepTestHelper(JvmMonitorAnalysis.StepState.readFromFile(file), generatedFlows);
                Map<String, Set<String>> found = helper.checkFlowStatistics(loadResult, 0.5f);
                if (previous != null && found != null) {
                    assertEquals(previous.keySet(), found.keySet());
                    for (Object[] pair : generatedFlows) {
                        GeneratedFlow generatedFlow = (GeneratedFlow) pair[0];
                        for (String prevFlowId : previous.get(generatedFlow.getId())) {
                            for (String foundFlowId : found.get(generatedFlow.getId())) {
                                String message = "Flows " + prevFlowId + " and " + foundFlowId + " cannot represent the same generated flow:\n" + generatedFlow;
                                assertTrue(message, helper.flowsMaybeSame(prevFlowId, foundFlowId));
                            }
                        }
                    }
                }
                previous = found;
            }
        }

        stopMonitoring();
        stopLoad();
    }

    private File prepareFolder(String path) {
        File folder = new File(path);
        if (folder.exists()) {
            for (File file : folder.listFiles()) file.delete();
        } else {
            folder.mkdirs();
        }
        return folder;
    }

    private void awaitNextSummary(int timeoutSec, Date begin) {
        logger.fine("Begin waiting for flow summary from " + begin);
        long border = System.currentTimeMillis() + timeoutSec * 1000;
        String oldMsg = "";
        while (System.currentTimeMillis() < border) {
            try {
                FlowSummary flowSummary = processedDataIndex.getLastSummary();
                if (logger.isLoggable(Level.FINE)) {
                    String msg = "Read flow summary " + (flowSummary == null ? "null" : "of time " + flowSummary.time);
                    if (!oldMsg.equals(msg))
                        logger.fine(msg);
                    oldMsg = msg;
                }
                if (flowSummary != null && (begin == null || flowSummary.time.after(begin))) {
                    return;
                }

                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }

        fail("Summary not produced in " + timeoutSec + " sec");
    }

}
