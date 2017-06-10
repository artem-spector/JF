package com.jflop.server.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.HttpTestClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 10/1/16
 */
public class RuntimeClient {

    public final String jvmId = UUID.randomUUID().toString();

    private String agentId;

    private HttpTestClient httpClient;

    private Map<String, Object> content = new HashMap<>();

    private ObjectMapper mapper = new ObjectMapper();

    public RuntimeClient(HttpTestClient httpClient, String agentId) {
        this.httpClient = httpClient;
        this.agentId = agentId;
    }

    public void ping() throws Exception {
        HttpTestClient.Request request = new HttpTestClient.Request(HttpMethod.POST, RuntimeController.RUNTIME_API_PATH + "/" + agentId + "/" + jvmId);
        request.header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        request.header("Accept", MediaType.APPLICATION_JSON_VALUE);
        request.body(mapper.writeValueAsString(content));
        HttpTestClient.Response response = httpClient.send(request);
        assertEquals(response.getContentAsString(), 200, response.statusCode);

        System.out.println("------ agent --------");
        System.out.println(response.getContentAsString());

    }
}
