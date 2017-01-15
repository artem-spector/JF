package com.jflop.load;

import com.jflop.server.runtime.ProcessedDataIndex;
import com.jflop.server.runtime.data.processed.FlowSummary;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * TODO: Document!
 *
 * @author artem on 15/01/2017.
 */
public class AnalysisTest extends LoadTestBase {

    @Autowired
    private ProcessedDataIndex processedDataIndex;

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
        LoadRunner.FlowStats flowStats = loadResult.flows.get(flow.getId());
        assertEquals(expectedThroughput, (float) flowStats.executed / loadResult.durationMillis / 1000, expectedThroughput / 100);
    }

}
