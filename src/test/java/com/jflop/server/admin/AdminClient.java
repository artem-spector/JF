package com.jflop.server.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

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
        MockHttpServletResponse response = sendRequestHandleAuth(request, true);
        return readAgentsFromResponse(response);
    }

    public String createAgent(String displayName) throws Exception {
        MockHttpServletRequestBuilder request = post(AdminController.AGENTS_PATH).param("name", displayName);
        MockHttpServletResponse response = sendRequestHandleAuth(request, true);
        assertEquals(HttpServletResponse.SC_CREATED, response.getStatus());

        String location = response.getHeader("location");
        return location.substring(location.lastIndexOf("/") + 1);
    }

    public void updateAgent(String agentId, String name) throws Exception {
        MockHttpServletRequestBuilder request = put(AdminController.AGENTS_PATH + "/" + agentId).param("name", name);
        MockHttpServletResponse response = sendRequestHandleAuth(request, true);
        assertEquals(200, response.getStatus());
    }

    public void deleteAgent(String agentId) throws Exception {
        MockHttpServletRequestBuilder request = delete(AdminController.AGENTS_PATH + "/" + agentId);
        MockHttpServletResponse response = sendRequestHandleAuth(request, true);
        assertEquals(200, response.getStatus());
    }

    public ZipInputStream downloadAgent(String agentId) throws Exception {
        MockHttpServletRequestBuilder request = get(AdminController.AGENTS_PATH + "/" + agentId + "/download");
        MockHttpServletResponse response = sendRequestHandleAuth(request, false);
        assertEquals(200, response.getStatus());

        return new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()));
    }

    private MockHttpServletResponse sendRequestHandleAuth(MockHttpServletRequestBuilder request, boolean doPrint) throws Exception {
        if (authHeader != null)
            request.header(AdminSecurityInterceptor.AUTH_HEADER, authHeader);

        ResultActions perform = mockMvc.perform(request);
        if (doPrint) perform.andDo(print());
        MockHttpServletResponse response = perform.andReturn().getResponse();
        if (response.getStatus() == 401) {
            obtainAuthHeader();
            response = sendRequestHandleAuth(request, true);
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


