package com.jflop.server.admin;

import com.jflop.HttpTestClient;
import com.jflop.server.ServerApp;
import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.admin.data.JFAgent;
import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.runtime.RuntimeClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.*;

/**
 * Test admin REST API
 *
 * @author artem
 *         Date: 7/2/16
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ServerApp.class)
@WebAppConfiguration
public class AdminTest {

    @Autowired
    private AccountIndex accountIndex;

    @Autowired
    private AgentJVMIndex agentJVMIndex;

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private AdminDAO adminDAO;

    private HttpTestClient testClient;
    private AdminClient adminClient;

    @Before
    public void setUp() throws Exception {
        accountIndex.deleteIndex();
        agentJVMIndex.deleteIndex();
        testClient = new HttpTestClient(MockMvcBuilders.webAppContextSetup(wac).build());
        adminClient = new AdminClient(testClient, "account_one");
        adminDAO.createAccount("account_one");
        accountIndex.refreshIndex();
    }

    @Test
    public void testAgentsCRUD() throws Exception {
        List<JFAgent> agents = adminClient.getAgents();
        assertEquals(0, agents.size());

        String name = "my first one";
        String id = adminClient.createAgent(name);
        agents = adminClient.getAgents();
        assertEquals(1, agents.size());
        assertEquals(id, agents.get(0).agentId);
        assertEquals(name, agents.get(0).agentName);

        name = "updated name";
        adminClient.updateAgent(id, name);
        agents = adminClient.getAgents();
        assertEquals(1, agents.size());
        assertEquals(id, agents.get(0).agentId);
        assertEquals(name, agents.get(0).agentName);

        adminClient.deleteAgent(id);
        agents = adminClient.getAgents();
        assertEquals(0, agents.size());
    }

    @Test
    public void testJvmCRUD() throws Exception {
        List<Map<String, Object>> agents = adminClient.getAgentsJson();
        assertEquals(0, agents.size());

        String name = "my first one";
        String agentId = adminClient.createAgent(name);
        accountIndex.refreshIndex();
        agents = adminClient.getAgentsJson();
        assertEquals(1, agents.size());

        Map<String, Object> agent = agents.get(0);
        assertEquals(name, agent.get("agentName"));
        assertEquals(Arrays.asList(AdminController.DEFAULT_FEATURES), agent.get("enabledFeatures"));
        assertEquals(0, ((Map) agent.get("jvms")).size());

        RuntimeClient runtimeClient1 = new RuntimeClient(testClient, agentId);
        long pingTime = System.currentTimeMillis();
        runtimeClient1.ping();
        agentJVMIndex.refreshIndex();
        agents = adminClient.getAgentsJson();
        agent = agents.get(0);
        Map<String, Map<String, Object>> jvms = (Map<String, Map<String, Object>>) agent.get("jvms");
        assertEquals(1, jvms.size());
        Map<String, Object> jvm1 = jvms.values().iterator().next();
        long lastReported = (long) jvm1.get("lastReportedAt");
        assertTrue(lastReported >= pingTime);

        RuntimeClient runtimeClient2 = new RuntimeClient(testClient, agentId);
        pingTime = System.currentTimeMillis();
        runtimeClient2.ping();
        agentJVMIndex.refreshIndex();
        agents = adminClient.getAgentsJson();
        agent = agents.get(0);
        jvms = (Map<String, Map<String, Object>>) agent.get("jvms");
        assertEquals(2, jvms.size());
        Map<String, Object> jvm2 = jvms.get(runtimeClient2.jvmId);
        lastReported = (long) jvm2.get("lastReportedAt");
        assertTrue(lastReported >= pingTime);


        System.out.println("--------------");
        System.out.println(agents);
        System.out.println("--------------");

        adminClient.deleteAgent(agentId);
        agents = adminClient.getAgentsJson();
        assertEquals(0, agents.size());
    }

    @Test
    public void testFeatureCommand() throws Exception {
        String agentId = adminClient.createAgent("agent one");
        accountIndex.refreshIndex();
        RuntimeClient runtimeClient = new RuntimeClient(testClient, agentId);
        runtimeClient.ping();
        agentJVMIndex.refreshIndex();

        // get configuration
        AgentJVM jvm = new AgentJVM(null, agentId, runtimeClient.jvmId);
        adminClient.submitCommand(jvm, InstrumentationConfigurationFeature.FEATURE_ID, InstrumentationConfigurationFeature.GET_CONFIG, null);
        agentJVMIndex.refreshIndex();

        runtimeClient.ping();
        agentJVMIndex.refreshIndex();
        List<Map<String, Object>> agents = adminClient.getAgentsJson();

        System.out.println("------- admin -------");
        System.out.println(agents);
    }

    @Test
    public void testDownloadAgent() throws Exception {
        String id = adminClient.createAgent("my agent");
        ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(adminClient.downloadAgent(id)));
        ZipEntry zipEntry;
        String propertiesFile = AdminController.JFSERVER_PROPERTIES_FILE;
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            if (zipEntry.getName().equals(propertiesFile))
                break;
        }

        assertNotNull("No configuration file " + propertiesFile, zipEntry);
        byte[] content = AdminController.readEntryContent(zipInputStream);
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(content));
        assertEquals(id, properties.getProperty("agent.id"));
    }

}
