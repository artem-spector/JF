package com.jflop.server.background;

import com.jflop.integration.IntegrationTestBase;
import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.feature.ClassInfoFeature;
import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.feature.JvmMonitorFeature;
import com.jflop.server.feature.SnapshotFeature;
import com.jflop.server.runtime.data.FlowMetadata;
import org.jflop.config.JflopConfiguration;
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
public class AnalysisTest extends IntegrationTestBase {

    private static final String MULTIPLE_FLOWS_PRODUCER_INSTRUMENTATION_PROPERTIES = "multipleFlowsProducer.instrumentation.properties";

    @Autowired
    private JvmMonitorAnalysis analysis;

    @Test
    public void testThreadsToFlows() throws Exception {
        startLoad(5);
        monitorJvm(3).get();
        setConfiguration(getJflopConfiguration(MULTIPLE_FLOWS_PRODUCER_INSTRUMENTATION_PROPERTIES));
        takeSnapshot(1);
        stopLoad();
        refreshAll();

        TaskLockData lock = new TaskLockData("threads to flow test", agentJVM);
        initStep(lock);
        analysis.mapThreadsToFlows();

        assertTrue((analysis.threads != null && !analysis.threads.isEmpty()));
        assertTrue(analysis.flows != null);
        assertEquals(2, analysis.flows.size());

        assertTrue(analysis.threadsToFlows != null);
        Set<FlowMetadata> distinctFlows = new HashSet<>();
        for (List<FlowMetadata> list : analysis.threadsToFlows.values()) {
            distinctFlows.addAll(list);
        }
        assertEquals(2, distinctFlows.size());
    }

    @Test
    public void testInstrumentThreads() throws Exception {
        Future future = monitorJvm(2);
        startLoad(15);
        future.get();
        stopLoad();
        refreshAll();

        // first pass - still no class metadata, the instrumented methods not set
        TaskLockData lock = new TaskLockData("instrument threads test", agentJVM);
        initStep(lock);
        analysis.mapThreadsToFlows();
        analysis.instrumentUncoveredThreads();
        analysis.afterStep(lock);
        assertTrue(analysis.methodsToInstrument == null || analysis.methodsToInstrument.isEmpty());

        // wait until the class metadata returns and try again
        awaitFeatureResponse(ClassInfoFeature.FEATURE_NAME, System.currentTimeMillis(), 3, null);
        future = monitorJvm(2);
        startLoad(15);
        future.get();
        stopLoad();
        refreshAll();

        initStep(lock);
        analysis.mapThreadsToFlows();
        analysis.instrumentUncoveredThreads();
        assertTrue(analysis.methodsToInstrument != null && !analysis.methodsToInstrument.isEmpty());
        List<MethodConfiguration> expected = getJflopConfiguration(MULTIPLE_FLOWS_PRODUCER_INSTRUMENTATION_PROPERTIES).getAllMethods();
        expected.removeAll(analysis.methodsToInstrument);
        assertTrue("The following methods not instrumented: " + expected, expected.isEmpty());
    }

    private void initStep(TaskLockData lock) {
        analysis.beforeStep(lock, new Date());
        System.out.println("Analyzing interval from " + analysis.from + " to " + analysis.to);
    }

    private Future monitorJvm(int durationSec) throws Exception {
        adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.ENABLE, null);
        analysis.stop(agentJVM);
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

    private void setConfiguration(JflopConfiguration conf) throws Exception {
        adminClient.submitCommand(agentJVM, InstrumentationConfigurationFeature.FEATURE_ID, InstrumentationConfigurationFeature.SET_CONFIG, configurationAsText(conf));
        FeatureCommand command = awaitFeatureResponse(InstrumentationConfigurationFeature.FEATURE_ID, System.currentTimeMillis(), 10, null);
        assertNull(command.errorText);
    }

    private JflopConfiguration getJflopConfiguration(String configurationFile) throws java.io.IOException {
        return new JflopConfiguration(getClass().getClassLoader().getResourceAsStream(configurationFile));
    }

    private void takeSnapshot(int durationSec) throws Exception {
        Map<String, Object> param = new HashMap<>();
        param.put("durationSec", String.valueOf(durationSec));

        adminClient.submitCommand(agentJVM, SnapshotFeature.FEATURE_ID, SnapshotFeature.TAKE_SNAPSHOT, param);
        FeatureCommand command = awaitFeatureResponse(SnapshotFeature.FEATURE_ID, System.currentTimeMillis(),
                durationSec + 5, latest -> latest.successText != null);
        System.out.println(command.successText);
    }
}
