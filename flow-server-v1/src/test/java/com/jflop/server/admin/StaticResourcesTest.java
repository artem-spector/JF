package com.jflop.server.admin;

import com.jflop.server.ServerApp;
import com.jflop.server.background.JvmMonitorAnalysis;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Check ResourceHandler mapping, see {@link com.jflop.server.CustomConfig}
 *
 * @author artem
 *         Date: 7/9/16
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ServerApp.class)
@WebAppConfiguration
public class StaticResourcesTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private JvmMonitorAnalysis analysis;

    private MockMvc mockMvc;

    @Before
    public void init() throws InterruptedException {
        analysis.stop();
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    public void getWebResources() throws Exception {
        getResource("/console/index.html");
        getResource("/console/js/adminApp.js");
        getResource("/console/js/adminApp.js");
    }

    private void getResource(String path) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        mockMvc.perform(request).andDo(print()).andExpect(status().isOk());
    }
}
