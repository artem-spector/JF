package com.jflop.integration;

import com.jflop.HttpTestClient;
import com.jflop.server.ServerApp;
import com.jflop.server.admin.AdminClient;
import com.jflop.server.admin.AdminDAO;
import com.jflop.server.admin.JFAgent;
import com.jflop.server.feature.Feature;
import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.feature.SnapshotFeature;
import com.sample.MultipleFlowsProducer;
import org.jflop.config.JflopConfiguration;
import org.jflop.snapshot.Snapshot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;

import static org.junit.Assert.*;

/**
 * Downloads agent and dynamically loads it into the current process.
 *
 * @author artem
 *         Date: 7/9/16
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ServerApp.class)
@WebIntegrationTest()
public class IntegrationTest {

    @Autowired
    private AdminDAO adminDAO;

    private static AdminClient adminClient;
    private static JFAgent agent;

    private MultipleFlowsProducer producer = new MultipleFlowsProducer();
    private boolean stopIt;

    @Before
    public void activateAgent() throws Exception {
        if (adminClient != null) return;

        HttpTestClient client = new HttpTestClient("http://localhost:8080");
        adminClient = new AdminClient(client, "testAccount");
        String agentId = this.adminClient.createAgent("testAgent");
        agent = adminDAO.getAgent(agentId);
        byte[] bytes = this.adminClient.downloadAgent(agentId);
        File file = new File("target/jflop-agent-test.jar");
        FileOutputStream out = new FileOutputStream(file);
        out.write(bytes);
        out.close();
        loadAgent(file.getPath());
    }

    @After
    public void clearConfiguration() {
        if (adminClient == null) return;

        InstrumentationConfigurationFeature configurationFeature = agent.getFeature(InstrumentationConfigurationFeature.class);
        configurationFeature.setAgentConfiguration(new JflopConfiguration());
        awaitFeatureResponse(configurationFeature, 3000);
    }

    @Test
    public void testAgentConnectivity() throws InterruptedException {
        long start = System.currentTimeMillis();
        assertNotNull(adminClient);
        Thread.sleep(1200);
        assertTrue(agent.lastReportTime.getTime() > start);
    }

    @Test
    public void testConfigurationFeature() throws IOException {
        InstrumentationConfigurationFeature feature = agent.getFeature(InstrumentationConfigurationFeature.class);

        // 1. get configuration and make sure it's empty
        feature.requestAgentConfiguration();
        awaitFeatureResponse(feature, 2100);
        JflopConfiguration conf = feature.getAgentConfiguration();
        assertNotNull(conf);
        assertTrue(conf.isEmpty());

        // 2. set configuration from a file
        conf = new JflopConfiguration(getClass().getClassLoader().getResourceAsStream("multipleFlowsProducer.instrumentation.properties"));
        feature.setAgentConfiguration(conf);
        awaitFeatureResponse(feature, 3000);
        assertEquals(conf, feature.getAgentConfiguration());
    }

    @Test
    public void testSnapshotFeature() throws IOException, InterruptedException {
        // 1. instrument multiple flows producer
        JflopConfiguration configuration = new JflopConfiguration(getClass().getClassLoader().getResourceAsStream("multipleFlowsProducer.instrumentation.properties"));
        InstrumentationConfigurationFeature configFeature = agent.getFeature(InstrumentationConfigurationFeature.class);
        configFeature.setAgentConfiguration(configuration);
        awaitFeatureResponse(configFeature, 3000);
        assertNull(configFeature.getError());

        // 2. take snapshot without load and make sure there are no flows
        SnapshotFeature snapshotFeature = agent.getFeature(SnapshotFeature.class);
        snapshotFeature.takeSnapshot(2);
        awaitFeatureResponse(snapshotFeature, 5000);
        assertNull(snapshotFeature.getError());
        Snapshot snapshot = snapshotFeature.getLastSnapshot();
        assertTrue(snapshot.getFlowMap().isEmpty());

        // 3. take snapshot under load and make sure all the flows are recorded
        startLoad(2);
        snapshotFeature.takeSnapshot(2);
        awaitFeatureResponse(snapshotFeature, 5000);
        assertNull(snapshotFeature.getError());
        snapshot = snapshotFeature.getLastSnapshot();
        System.out.println(snapshot.format(0, 0));
        assertEquals(2, snapshot.getFlowMap().size());
    }

    private void loadAgent(String path) throws Exception {
        String nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName();
        int p = nameOfRunningVM.indexOf('@');
        String pid = nameOfRunningVM.substring(0, p);

        try {
            com.sun.tools.attach.VirtualMachine vm = com.sun.tools.attach.VirtualMachine.attach(pid);
            vm.loadAgent(path, "");
            vm.detach();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void awaitFeatureResponse(Feature feature, long timeoutMillis) {
        assertNotNull(feature.getProgress());
        long interval = Math.max(100, timeoutMillis / 10);
        long timeout = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < timeout) {
            if (feature.getProgress() == null) return;
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        fail("Feature " + feature.name + " has not respond after " + timeoutMillis + "ms.");
    }

    private void startLoad(int numThreads) {
        stopIt = false;
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread("ProcessingThread_" + i) {
                public void run() {
                    for (int i = 1; !stopIt; i++) {
                        String user = "usr" + i;
                        for (int j = 0; j < 20; j++) {
                            try {
                                producer.serve(user);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            };
            threads[i].start();
        }
    }

    private void stopLoad() {
        stopIt = true;
    }
}
