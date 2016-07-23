package com.jflop.server.admin;

import com.jflop.HttpTestClient;
import com.jflop.server.ServerApp;
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
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
    private WebApplicationContext wac;

    @Autowired
    private AdminDAO adminDAO;

    private AdminClient client;

    @Before
    public void setUp() throws Exception {
        HttpTestClient client = new HttpTestClient(MockMvcBuilders.webAppContextSetup(wac).build());
        this.client = new AdminClient(client, "account_one");
        adminDAO.createAccount("account_one");
    }

    @Test
    public void testAgentsCRUD() throws Exception {
        List<JFAgent> agents = client.getAgents();
        assertEquals(0, agents.size());

        String name = "my first one";
        String id = client.createAgent(name);
        agents = client.getAgents();
        assertEquals(1, agents.size());
        assertEquals(id, agents.get(0).agentId);
        assertEquals(name, agents.get(0).name);

        name = "updated name";
        client.updateAgent(id, name);
        agents = client.getAgents();
        assertEquals(1, agents.size());
        assertEquals(id, agents.get(0).agentId);
        assertEquals(name, agents.get(0).name);

        client.deleteAgent(id);
        agents = client.getAgents();
        assertEquals(0, agents.size());
    }

    @Test
    public void testDownloadAgent() throws Exception {
        String id = client.createAgent("my agent");
        ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(client.downloadAgent(id)));
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
