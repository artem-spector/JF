package com.jflop.integration;

import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.feature.JvmMonitorFeature;
import com.jflop.server.feature.SnapshotFeature;
import com.jflop.server.runtime.MetadataIndex;
import com.jflop.server.runtime.RawDataIndex;
import com.jflop.server.runtime.data.FlowMetadata;
import com.jflop.server.runtime.data.ThreadMetadata;
import org.jflop.config.JflopConfiguration;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author artem
 *         Date: 7/9/16
 */
public class FeaturesIntegrationTest extends IntegrationTestBase {


    @Autowired
    private RawDataIndex rawDataIndex;

    @Autowired
    private MetadataIndex metadataIndex;

    @Autowired
    private InstrumentationConfigurationFeature configurationFeature;

    @Autowired
    private SnapshotFeature snapshotFeature;

    @Test
    public void testConfigurationFeature() throws Exception {
        String featureId = InstrumentationConfigurationFeature.FEATURE_ID;

        // 1. submit empty configuration
        JflopConfiguration conf= setConfiguration(new JflopConfiguration());
        System.out.println(configurationAsText(conf));

        // 2. get configuration and make sure it's empty
        adminClient.submitCommand(agentJVM, featureId, InstrumentationConfigurationFeature.GET_CONFIG, null);
        FeatureCommand command = awaitFeatureResponse(featureId, System.currentTimeMillis(), 10, null);
        conf = new JflopConfiguration(new ByteArrayInputStream(command.successText.getBytes()));
        assertTrue(conf.isEmpty());

        // 3. set configuration from a file
        JflopConfiguration expected = loadInstrumentationConfiguration(MULTIPLE_FLOWS_PRODUCER_INSTRUMENTATION_PROPERTIES);
        setConfiguration(expected);
        adminClient.submitCommand(agentJVM, featureId, InstrumentationConfigurationFeature.GET_CONFIG, null);
        command = awaitFeatureResponse(featureId, System.currentTimeMillis(), 10, null);
        conf = new JflopConfiguration(new ByteArrayInputStream(command.successText.getBytes()));
        System.out.println(configurationAsText(conf));
        assertEquals(expected, conf);
    }

    @Test
    public void testSnapshotFeature() throws Exception {
        // 1. instrument multiple flows producer
        JflopConfiguration expectedConfig = loadInstrumentationConfiguration(MULTIPLE_FLOWS_PRODUCER_INSTRUMENTATION_PROPERTIES);
        setConfiguration(expectedConfig);

        // 2. take snapshot without load and make sure there are no flows
        stopLoad();
        String successText = takeSnapshot(2);
        System.out.println(successText);
        assertTrue(successText.contains("contains no flows."));


        // 3. take snapshot under load and make sure all the flows are recorded
        startLoad(10);
        Date begin = new Date();
        successText = takeSnapshot(2);
        System.out.println(successText);
        assertTrue(successText.contains("2 distinct flows"));

        stopLoad();

        metadataIndex.refreshIndex();
        List<FlowMetadata> found = metadataIndex.findMetadata(agentJVM, FlowMetadata.class, begin, 1);
        assertTrue(found != null && found.size() == 1);
        FlowMetadata flow = found.get(0);
        assertNotNull(flow.instrumentedMethodsJson);
        assertEquals(expectedConfig, JflopConfiguration.fromJson(flow.instrumentedMethodsJson));
    }

    @Test
    public void testJvmMonitorFeature() throws Exception {
        adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.ENABLE, null);
        FeatureCommand command = awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10, null);
        System.out.println(command.successText);
        assertTrue(command.successText.contains("process CPU load:"));

        adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.DISABLE, null);
        long submitted = System.currentTimeMillis();
        command = awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, submitted, 10, null);
        System.out.println(command.successText);
        if (!command.successText.contains("OK")) {
            // the first response may come before the command was received by the client, so wait for the next change
            command = awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, command.respondedAt.getTime(), 10, null);
        }
        assertTrue(command.successText.contains("OK"));
    }

    @Test
    public void testThreadDumpMetadata() throws Exception {
        // 1. no flows in the beginning
        Date begin = new Date();
        List<ThreadMetadata> existing = metadataIndex.findMetadata(agentJVM, ThreadMetadata.class, begin, 1000);
        assertEquals(0, existing.size());

        // 2. enable monitor feature, and make sure there are some flows detected, and all have different stack traces
        adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.ENABLE, null);
        FeatureCommand command = awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10, null);
        System.out.println(command.successText);

        metadataIndex.refreshIndex();
        existing = metadataIndex.findMetadata(agentJVM, ThreadMetadata.class, begin, 1000);
        System.out.println("Number of thread dumps: " + existing.size());
        assertTrue("No thread dump metadata found for accountId " + agentJVM.accountId, existing.size() > 0);

        // 3. wait for another report and make sure the number of threads was not duplicated
        command = awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10, null);
        System.out.println(command.successText);
        metadataIndex.refreshIndex();
        int oldSize = existing.size();
        existing = metadataIndex.findMetadata(agentJVM, ThreadMetadata.class, begin, 1000);
        System.out.println("Number of thread dumps: " + existing.size());
        assertTrue(oldSize < 2 * existing.size());

        // 4. turn on load and make sure there is at least 1 new thread dump
        // the flow of initializing user cache happens in the beginning of a thread and is unlikely to be caught by the thread dump
        System.out.println("start load..");
        startLoad(5);

        for (int i = 0; i < 1; i++)
            command = awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10, null);
        System.out.println(command.successText);
        metadataIndex.refreshIndex();
        oldSize = existing.size();
        existing = metadataIndex.findMetadata(agentJVM, ThreadMetadata.class, begin, 1000);
        System.out.println("Number of thread dumps: " + existing.size());
        assertTrue(existing.size() >= oldSize + 1);

        System.out.println("stop load..");
        stopLoad();
        adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.DISABLE, null);
    }

}
