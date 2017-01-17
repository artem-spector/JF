package com.jflop.load;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * TODO: Document!
 *
 * @author artem on 15/01/2017.
 */
public class LoadTestInfra extends LoadTestBase {

    public LoadTestInfra() {
        super("LoadTestInfra", false);
    }

    @Test
    public void testStartLoadClient() throws Exception {
        String agentName = "reusableAgent";
        int numIterations = 3;

        for (int i = 0; i < numIterations; i++) {
            startClient(agentName);
            assertNotNull(currentJvm);
            logger.info("Started JVM for agent " + agentName + "->" + currentJvm.jvmId);
            Thread.sleep(2000);
            stopClient();
        }
    }

    @Test
    public void testSetLoad() throws Exception {
        startClient("loadAgent");
        generateFlows(3, 10, 100, 0, 10);
        startLoad();
        Thread.sleep(2000);
        stopLoad();
    }

}
