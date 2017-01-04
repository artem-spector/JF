package com.jflop.server.background;

import com.jflop.integration.FeaturesIntegrationTest;
import com.jflop.server.feature.ClassInfoFeature;
import com.jflop.server.feature.JvmMonitorFeature;
import com.jflop.server.feature.SnapshotFeature;
import com.jflop.server.runtime.data.FlowMetadata;
import com.jflop.server.runtime.data.FlowOccurrenceData;
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
        logger.fine("================== testMapThreadsToFlows ==================");
        TaskLockData lock = new TaskLockData("threads to flow test", agentJVM);

        startLoad(5);
        monitorJvm(2).get();
        setConfiguration(loadInstrumentationConfiguration(MULTIPLE_FLOWS_PRODUCER_INSTRUMENTATION_PROPERTIES));
        String snapshot = takeSnapshot(1);
        logger.fine(snapshot);
        stopLoad();
        refreshAll();

        initStep(lock);
        analysis.mapThreadsToFlows();

        assertTrue((analysis.step.get().threads != null && !analysis.step.get().threads.isEmpty()));
        assertTrue(analysis.step.get().flows != null);
        assertEquals("detected flows: " + analysis.step.get().flows.keySet(), 2, analysis.step.get().flows.keySet().size());

        assertTrue(analysis.step.get().threadsToFlows != null);
        Set<FlowMetadata> distinctFlows = new HashSet<>();
        analysis.step.get().threadsToFlows.values().forEach(distinctFlows::addAll);
        assertEquals("Flows mapped to threads: " + distinctFlows, 2, distinctFlows.size());
    }

    @Test
    public void testInstrumentThreads() throws Exception {
        TaskLockData lock = new TaskLockData("instrument threads test", agentJVM);
        Future future = monitorJvm(2);
        startLoad(15);
        future.get();
        refreshAll();

        // first pass - still no class metadata, the instrumented methods not set
        initStep(lock);
        analysis.mapThreadsToFlows();
        analysis.instrumentUncoveredThreads();
        assertTrue(analysis.step.get().methodsToInstrument == null || analysis.step.get().methodsToInstrument.isEmpty());
        analysis.afterStep(lock);

        // wait until the class metadata returns and try again
        awaitFeatureResponse(ClassInfoFeature.FEATURE_NAME, System.currentTimeMillis(), 5, null);
        future = monitorJvm(1);
        future.get();
        stopLoad();
        refreshAll();

        initStep(lock);
        analysis.mapThreadsToFlows();
        analysis.instrumentUncoveredThreads();
        assertTrue(analysis.step.get().methodsToInstrument != null && !analysis.step.get().methodsToInstrument.isEmpty());
        List<MethodConfiguration> expected = loadInstrumentationConfiguration(MULTIPLE_FLOWS_PRODUCER_INSTRUMENTATION_PROPERTIES).getAllMethods();
        expected.removeAll(analysis.step.get().methodsToInstrument);
        assertTrue("The following methods not instrumented: " + expected, expected.isEmpty());
    }

    @Test
    public void testAnalyze() throws Exception {
        analyzeUntilNextSnapshot(30);
        analyzeUntilNextSnapshot(10);
    }

    private void analyzeUntilNextSnapshot(int timeoutSec) throws Exception {
        TaskLockData lock = new TaskLockData("analyze test", agentJVM);
        Date from = lock.processedUntil;
        long until = System.currentTimeMillis() + timeoutSec * 1000;
        boolean gotIt = false;
        startLoad(15);
        while (!gotIt && System.currentTimeMillis() < until) {
            Future future = monitorJvm(2);
            future.get();
            refreshAll();

            initStep(lock);
            analysis.analyze();

            if (analysis.step.get().flows != null) {
                for (List<FlowOccurrenceData> list : analysis.step.get().flows.values()) {
                    for (FlowOccurrenceData occurenceData : list) {
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
            logger.fine(snapshot);
        } else {
            fail("Did not get a snapshot in " + timeoutSec + " sec");
        }

    }

    private void initStep(TaskLockData lock) {
        analysis.beforeStep(lock, new Date());
        logger.fine("Analyzing interval from " + analysis.step.get().from + " to " + analysis.step.get().to);
    }

    private Future monitorJvm(int durationSec) throws Exception {
        adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.ENABLE, null);
        awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10, null);
        logger.fine("start monitoring JVM (" + durationSec + " sec)");

        return Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Thread.sleep(durationSec * 1000);

                adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.DISABLE, null);
                awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10, null);
                logger.fine("stop monitoring JVM");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

}
