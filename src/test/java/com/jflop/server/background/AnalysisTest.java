package com.jflop.server.background;

import com.jflop.integration.IntegrationTestBase;
import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.feature.JvmMonitorFeature;
import com.jflop.server.feature.SnapshotFeature;
import com.jflop.server.runtime.data.FlowMetadata;
import org.jflop.config.JflopConfiguration;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * TODO: Document!
 *
 * @author artem on 21/12/2016.
 */
public class AnalysisTest extends IntegrationTestBase {

    @Autowired
    private JvmMonitorAnalysis analysis;

    @Test
    public void testThreadsToFlows() throws Exception {
        startLoad(5);
        monitorJvm(3);
        setConfiguration("multipleFlowsProducer.instrumentation.properties");
        takeSnapshot(1);
        refreshAll();
        stopLoad();

        step(new TaskLockData("analysis test", agentJVM));

        assertTrue((analysis.threads != null && !analysis.threads.isEmpty()));
        assertTrue(analysis.flows != null);
        assertEquals(2, analysis.flows.size());

        assertTrue(analysis.threadsToFlows != null);
        int flowCount = 0;
        for (List<FlowMetadata> list : analysis.threadsToFlows.values()) {
            flowCount += list.size();
        }
        assertEquals(2, flowCount);
    }

    private void step(TaskLockData lock) {
        analysis.agentJvm = agentJVM;

        Date from = lock.processedUntil;
        lock.processedUntil = new Date();
        analysis.mapThreadsToFlows(from, lock.processedUntil);

        analysis.step(lock, new Date());
    }

    private void monitorJvm(int durationSec) throws Exception {
        adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.ENABLE, null);
        analysis.stop(agentJVM);
        awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10, null);
        System.out.println("start monitoring JVM");

        Thread.sleep(durationSec * 1000);

        adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.DISABLE, null);
        awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10, null);
        System.out.println("stop monitoring JVM");
    }

    private void setConfiguration(String configurationFile) throws Exception {
        JflopConfiguration conf = new JflopConfiguration(getClass().getClassLoader().getResourceAsStream(configurationFile));
        adminClient.submitCommand(agentJVM, InstrumentationConfigurationFeature.FEATURE_ID, InstrumentationConfigurationFeature.SET_CONFIG, configurationAsText(conf));
        FeatureCommand command = awaitFeatureResponse(InstrumentationConfigurationFeature.FEATURE_ID, System.currentTimeMillis(), 10, null);
        assertNull(command.errorText);
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
