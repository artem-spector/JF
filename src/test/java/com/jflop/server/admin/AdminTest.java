package com.jflop.server.admin;

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

import java.util.List;

import static org.junit.Assert.assertEquals;

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

    private AdminClient client;

    @Before
    public void setUp() throws Exception {
        client = new AdminClient(MockMvcBuilders.webAppContextSetup(wac).build());
    }

    @Test
    public void testGetAgents() throws Exception {
        List<JFAgent> agents = client.getAgents();
        assertEquals(0, agents.size());
    }


}
