package com.jflop.server.rest.admin;

import com.jflop.server.rest.JfServerRestApp;
import com.jflop.server.rest.admin.data.JFAgent;
import com.jflop.server.rest.http.HttpTestClient;
import com.jflop.server.rest.runtime.RuntimeClient;
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
@SpringApplicationConfiguration(classes = JfServerRestApp.class)
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

        RuntimeClient runtimeClient1 = new RuntimeClient(testClient, agentId);
        long pingTime = System.currentTimeMillis();
        runtimeClient1.ping();
        agentJVMIndex.refreshIndex();
        agents = adminClient.getAgentsJson();
        Map<String, Object> agent = agents.get(0);
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
