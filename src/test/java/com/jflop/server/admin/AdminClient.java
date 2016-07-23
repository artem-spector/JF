package com.jflop.server.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.HttpTestClient;
import org.springframework.http.HttpMethod;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Mock of admin UI
 *
 * @author artem
 *         Date: 7/2/16
 */
public class AdminClient {

    private ObjectMapper mapper = new ObjectMapper();

    private HttpTestClient httpClient;

    private String credentials;
    private String authHeader;

    public AdminClient(HttpTestClient httpClient, String credentials) {
        this.credentials = credentials;
        this.httpClient = httpClient;
    }

    public List<JFAgent> getAgents() throws Exception {
        HttpTestClient.Request request = new HttpTestClient.Request(HttpMethod.GET, AdminController.AGENTS_PATH);
        HttpTestClient.Response response = sendRequestHandleAuth(request);
        return readAgentsFromResponse(response);
    }

    public String createAgent(String displayName) throws Exception {
        HttpTestClient.Request request = new HttpTestClient.Request(HttpMethod.POST, AdminController.AGENTS_PATH).param("name", displayName);
        HttpTestClient.Response response = sendRequestHandleAuth(request);
        assertEquals(HttpServletResponse.SC_CREATED, response.statusCode);

        String location = response.getHeader("location");
        return location.substring(location.lastIndexOf("/") + 1);
    }

    public void updateAgent(String agentId, String name) throws Exception {
        HttpTestClient.Request request = new HttpTestClient.Request(HttpMethod.PUT, AdminController.AGENTS_PATH + "/" + agentId).param("name", name);
        HttpTestClient.Response response = sendRequestHandleAuth(request);
        assertEquals(200, response.statusCode);
    }

    public void deleteAgent(String agentId) throws Exception {
        HttpTestClient.Request request = new HttpTestClient.Request(HttpMethod.DELETE, AdminController.AGENTS_PATH + "/" + agentId);
        HttpTestClient.Response response = sendRequestHandleAuth(request);
        assertEquals(200, response.statusCode);
    }

    public byte[] downloadAgent(String agentId) throws Exception {
        HttpTestClient.Request request = new HttpTestClient.Request(HttpMethod.GET, AdminController.AGENTS_PATH + "/" + agentId + "/download");
        HttpTestClient.Response response = sendRequestHandleAuth(request);
        assertEquals(200, response.statusCode);
        return response.getContentAsBytes();
    }

    private HttpTestClient.Response sendRequestHandleAuth(HttpTestClient.Request request) throws Exception {
        if (authHeader != null)
            request.header(AdminSecurityInterceptor.AUTH_HEADER, authHeader);

        HttpTestClient.Response response = httpClient.send(request);
        if (response.statusCode == 401) {
            obtainAuthHeader();
            response = sendRequestHandleAuth(request);
        }

        return response;
    }

    private List<JFAgent> readAgentsFromResponse(HttpTestClient.Response response) throws java.io.IOException {
        assertEquals(200, response.statusCode);
        List maps = mapper.readValue(response.getContentAsString(), List.class);
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


