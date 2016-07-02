package com.jflop.server.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private String authHeader;

    public AdminClient(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public List<JFAgent> getAgents() throws Exception {
        MockHttpServletRequestBuilder request = get(AdminController.AGENTS_PATH);
        MockHttpServletResponse response = sendRequestHandleAuth(request);
        assertEquals(200, response.getStatus());
        String str = response.getContentAsString();
        return mapper.readValue(str, List.class);
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

    private void obtainAuthHeader() {
            authHeader = "account_one";
    }
}


