package com.jflop.load;

import com.jflop.server.runtime.ProcessedDataIndex;
import com.jflop.server.runtime.data.processed.FlowSummary;
import com.jflop.server.runtime.data.processed.MethodCall;
import com.jflop.server.util.DebugPrintUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

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
        stopMonitoring();
        stopLoad();
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

    private void printSummary(FlowSummary summary) {
        System.out.println("----------- summary ------------------");
        for (MethodCall root : summary.roots) {
            System.out.println("\n" + DebugPrintUtil.methodCallSummaryStr("", root));
        }
        System.out.println("--------------------------------------");
    }
}
