package com.jflop.server.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

/**
 * Mock of admin UI
 *
 * @author artem
 *         Date: 7/2/16
 */
public class AdminClient {

    private ObjectMapper mapper = new ObjectMapper();

    private MockMvc mockMvc;

    private String credentials;
    private String authHeader;

    public AdminClient(MockMvc mockMvc, String credentials) {
        this.credentials = credentials;
        this.mockMvc = mockMvc;
    }

    public List<JFAgent> getAgents() throws Exception {
        MockHttpServletRequestBuilder request = get(AdminController.AGENTS_PATH);
        MockHttpServletResponse response = sendRequestHandleAuth(request);
        return readAgentsFromResponse(response);
    }

    public String createAgent(String displayName) throws Exception {
        MockHttpServletRequestBuilder request = post(AdminController.AGENTS_PATH).param("name", displayName);
        MockHttpServletResponse response = sendRequestHandleAuth(request);
        assertEquals(HttpServletResponse.SC_CREATED, response.getStatus());

        String location = response.getHeader("location");
        return location.substring(location.lastIndexOf("/") + 1);
    }

    public void updateAgent(String agentId, String name) throws Exception {
        MockHttpServletRequestBuilder request = put(AdminController.AGENTS_PATH + "/" + agentId).param("name", name);
        MockHttpServletResponse response = sendRequestHandleAuth(request);
        assertEquals(200, response.getStatus());
    }

    public void deleteAgent(String agentId) throws Exception {
        MockHttpServletRequestBuilder request = delete(AdminController.AGENTS_PATH + "/" + agentId);
        MockHttpServletResponse response = sendRequestHandleAuth(request);
        assertEquals(200, response.getStatus());
    }

    private MockHttpServletResponse sendRequestHandleAuth(MockHttpServletRequestBuilder request) throws Exception {
        if (authHeader != null)
            request.header(AdminSecurityInterceptor.AUTH_HEADER, authHeader);

        MockHttpServletResponse response = mockMvc.perform(request).andDo(print()).andReturn().getResponse();
        if (response.getStatus() == 401) {
            obtainAuthHeader();
            response = sendRequestHandleAuth(request);
        }

        return response;
    }

    private List<JFAgent> readAgentsFromResponse(MockHttpServletResponse response) throws java.io.IOException {
        assertEquals(200, response.getStatus());
        String str = response.getContentAsString();
        List maps = mapper.readValue(str, List.class);
        List<JFAgent> res = new ArrayList<>();
        for (Object map : maps) {
            res.add(mapper.readValue(mapper.writeValueAsString(map), JFAgent.class));
        }

        return res;
    }

    private void obtainAuthHeader() {
            authHeader = credentials;
    }
}


