package com.jflop.load;

import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.runtime.ProcessedDataIndex;
import com.jflop.server.runtime.data.processed.FlowSummary;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.Set;

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
    private InstrumentationConfigurationFeature instrumentationConfigurationFeature;

    public AnalysisTest() {
        super("AnalysisTest");
    }

    @Before
    public void startClient() throws Exception {
        startClient("analysisTestAgent");
    }

    @Test
    public void testSingleFlow() throws Exception {
        startLoad(1, 10, 10, 100, 100);
        startMonitoring();
        FlowSummary summary = awaitNextSummary(30);
        assertNotNull(summary);
        LoadRunner.LoadResult loadResult = stopLoad();
        stopMonitoring();

        GeneratedFlow flow = (GeneratedFlow) flowsAndThroughput[0][0];
        float expectedThroughput = (float) flowsAndThroughput[0][1];
        checkFlowStatistics(flow, expectedThroughput, loadResult, summary);
    }

    private FlowSummary awaitNextSummary(int timeoutSec) {
        Date begin = new Date();
        long border = System.currentTimeMillis() + timeoutSec * 1000;
        while (System.currentTimeMillis() < border) {
            try {
                FlowSummary summary = processedDataIndex.getLastSummary();
                if (summary != null && summary.time.after(begin))
                    return summary;

                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }

        fail("Summary not produced in " + timeoutSec + " sec");
        return null;
    }

    private void checkFlowStatistics(GeneratedFlow flow, float expectedThroughput, LoadRunner.LoadResult loadResult, FlowSummary summary) {
        Set<String> found = flow.findFlowIds(summary, instrumentationConfigurationFeature.getConfiguration(currentJvm));
        assertFalse("Flow not found in the summary:\n" + flow.toString(), found.isEmpty());
    }

}
