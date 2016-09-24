package com.sample;

import com.jflop.HttpTestClient;
import com.jflop.server.admin.AdminClient;
import com.jflop.server.take2.admin.data.JFAgent;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.management.ManagementFactory;

/**
 * "Client" process being profiled.
 * On startup downloads the agent from JFlop server, and loads that agents.
 * <p>
 * This class should be run after the {@link com.jflop.server.ServerApp} is up and running.
 *
 * @author artem
 *         Date: 7/30/16
 */
public class StandaloneClient {

    public static final String ACCOUNT_NAME = "sample";
    public static final String AGENT_NAME = "standaloneClient";

    private File agentFile;
    private MultipleFlowsProducer producer = new MultipleFlowsProducer();

    public static void main(String[] args) throws Exception {
        StandaloneClient client = new StandaloneClient();
        client.downloadAgent();
        client.activateAgent();
        client.startLoad(2);
    }

    private void downloadAgent() throws Exception {
        HttpTestClient client = new HttpTestClient("http://localhost:8080");
        AdminClient adminClient = new AdminClient(client, ACCOUNT_NAME);

        String agentId = null;
        for (JFAgent agent : adminClient.getAgents()) {
            if (agent.agentName.equals(AGENT_NAME)) {
                agentId = agent.agentId;
                System.out.println("Agent " + AGENT_NAME + " already exists.");
                break;
            }
        }
        if (agentId == null)
            agentId = adminClient.createAgent(AGENT_NAME);

        byte[] bytes = adminClient.downloadAgent(agentId);
        agentFile = new File("target/jflop-agent-" + AGENT_NAME + ".jar");
        FileOutputStream out = new FileOutputStream(agentFile);
        out.write(bytes);
        out.close();
        System.out.println("Agent jar downloaded to the file " + agentFile.getAbsolutePath());
    }

    private void activateAgent() throws Exception {
        if (agentFile != null) {
            String nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName();
            int p = nameOfRunningVM.indexOf('@');
            String pid = nameOfRunningVM.substring(0, p);

            com.sun.tools.attach.VirtualMachine vm = com.sun.tools.attach.VirtualMachine.attach(pid);
            vm.loadAgent(agentFile.getAbsolutePath(), "");
            vm.detach();
        }
    }

    private void startLoad(int numThreads) {
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread("ProcessingThread_" + i) {
                public void run() {
                    for (int i = 1; true; i++) {
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
}
