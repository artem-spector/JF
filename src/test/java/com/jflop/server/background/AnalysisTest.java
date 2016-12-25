package com.jflop.server.background;

import com.jflop.integration.FeaturesIntegrationTest;
import com.jflop.server.feature.ClassInfoFeature;
import com.jflop.server.feature.JvmMonitorFeature;
import com.jflop.server.feature.SnapshotFeature;
import com.jflop.server.runtime.data.FlowMetadata;
import com.jflop.server.runtime.data.FlowOccurenceData;
import org.jflop.config.MethodConfiguration;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

/**
 * TODO: Document!
 *
 * @author artem on 21/12/2016.
 */
public class AnalysisTest extends FeaturesIntegrationTest {

    @Autowired
    private SnapshotFeature snapshotFeature;

    @Test
    public void testMapThreadsToFlows() throws Exception {
        TaskLockData lock = new TaskLockData("threads to flow test", agentJVM);
        lock.processedUntil = new Date();

        startLoad(5);
        monitorJvm(2).get();
        setConfiguration(loadInstrumentationConfiguration(MULTIPLE_FLOWS_PRODUCER_INSTRUMENTATION_PROPERTIES));
        takeSnapshot(1);
        stopLoad();
        refreshAll();

        initStep(lock);
        analysis.mapThreadsToFlows();

        assertTrue((analysis.threads != null && !analysis.threads.isEmpty()));
        assertTrue(analysis.flows != null);
        assertEquals("detected flows: " + analysis.flows.keySet(), 2, analysis.flows.keySet().size());

        assertTrue(analysis.threadsToFlows != null);
        Set<FlowMetadata> distinctFlows = new HashSet<>();
        for (List<FlowMetadata> list : analysis.threadsToFlows.values()) {
            distinctFlows.addAll(list);
        }
        assertEquals("Flows mapped to threads: " + distinctFlows, 2, distinctFlows.size());
    }

    @Test
    public void testInstrumentThreads() throws Exception {
        Future future = monitorJvm(1);
        startLoad(15);
        future.get();
        refreshAll();

        // first pass - still no class metadata, the instrumented methods not set
        TaskLockData lock = new TaskLockData("instrument threads test", agentJVM);
        initStep(lock);
        analysis.mapThreadsToFlows();
        analysis.instrumentUncoveredThreads();
        analysis.afterStep(lock);
        assertTrue(analysis.methodsToInstrument == null || analysis.methodsToInstrument.isEmpty());

        // wait until the class metadata returns and try again
        awaitFeatureResponse(ClassInfoFeature.FEATURE_NAME, System.currentTimeMillis(), 5, null);
        future = monitorJvm(1);
        future.get();
        stopLoad();
        refreshAll();

        initStep(lock);
        analysis.mapThreadsToFlows();
        analysis.instrumentUncoveredThreads();
        assertTrue(analysis.methodsToInstrument != null && !analysis.methodsToInstrument.isEmpty());
        List<MethodConfiguration> expected = loadInstrumentationConfiguration(MULTIPLE_FLOWS_PRODUCER_INSTRUMENTATION_PROPERTIES).getAllMethods();
        expected.removeAll(analysis.methodsToInstrument);
        assertTrue("The following methods not instrumented: " + expected, expected.isEmpty());
    }

    @Test
    public void testAnalyze() throws Exception {
        analyzeUntilNextSnapshot(30);
        analyzeUntilNextSnapshot(10);
    }

    private void analyzeUntilNextSnapshot(int timeoutSec) throws Exception {
        Date from = new Date();
        long until = System.currentTimeMillis() + timeoutSec * 1000;
        TaskLockData lock = new TaskLockData("analyze test", agentJVM);
        boolean gotIt = false;
        startLoad(15);
        while (!gotIt && System.currentTimeMillis() < until) {
            Future future = monitorJvm(2);
            future.get();
            refreshAll();

            initStep(lock);
            analysis.analyze();

            if (analysis.flows != null) {
                for (List<FlowOccurenceData> list : analysis.flows.values()) {
                    for (FlowOccurenceData occurenceData : list) {
                        if (occurenceData.time.after(from)) {
                            gotIt = true;
                            break;
                        }
                    }
                }
            }

            if (!gotIt) {
                analysis.takeSnapshot();
                analysis.afterStep(lock);
            }
        }
        stopLoad();

        if (gotIt) {
            String snapshot = snapshotFeature.getLastSnapshot(agentJVM);
            assertNotNull(snapshot);
            System.out.println(snapshot);
        } else {
            fail("Did not get a snapshot in " + timeoutSec + " sec");
        }

    }

    private void initStep(TaskLockData lock) {
        analysis.beforeStep(lock, new Date());
        System.out.println("Analyzing interval from " + analysis.from + " to " + analysis.to);
    }

    private Future monitorJvm(int durationSec) throws Exception {
        adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.ENABLE, null);
        awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10, null);
        System.out.println("start monitoring JVM (" + durationSec + " sec)");

        return Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Thread.sleep(durationSec * 1000);

                adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.DISABLE, null);
                awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10, null);
                System.out.println("stop monitoring JVM");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

}
