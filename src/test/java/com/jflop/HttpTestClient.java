package com.jflop;

import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 7/23/16
 */
public class HttpTestClient {

    private MockMvc mockMvc;
    private String serverUrl;

    public HttpTestClient(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public HttpTestClient(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public Response send(Request request) throws Exception {
        if (mockMvc != null) {
            MockHttpServletRequestBuilder req = MockMvcRequestBuilders.request(request.method, request.path);
            for (Map.Entry<String, String> entry : request.params.entrySet()) {
                req.param(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, String> entry : request.headers.entrySet()) {
                req.header(entry.getKey(), entry.getValue());
            }
            MockHttpServletResponse response = mockMvc.perform(req).andReturn().getResponse();
            Map<String, String> headers = new HashMap<>();
            for (String header : response.getHeaderNames()) {
                headers.put(header.toLowerCase(), response.getHeader(header));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(response.getContentAsByteArray());
            return new Response(response.getStatus(), out, headers);

        } else if (serverUrl != null) {
            String path = serverUrl + request.path;

            String paramStr = request.getParamStr();
            if (request.method == HttpMethod.GET && !paramStr.isEmpty())
                path += "?" + paramStr;

            HttpURLConnection conn = (HttpURLConnection) new URL(path).openConnection();
            conn.setRequestMethod(request.method.name());
            for (Map.Entry<String, String> entry : request.headers.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }

            if (request.method != HttpMethod.GET) {
                String bodyStr = request.getParamStr() + request.body;
                if (!bodyStr.isEmpty()) {
                    conn.setDoOutput(true);
                    OutputStream out = conn.getOutputStream();
                    out.write(bodyStr.getBytes());
                    out.flush();
                }
            }

            int code = conn.getResponseCode();

            Map<String, String> headers = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                String name = entry.getKey();
                headers.put(name == null ? null : name.toLowerCase(), entry.getValue().get(0));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                InputStream in = conn.getInputStream();
                byte buf[] = new byte[512];
                int count;
                while ((count = in.read(buf)) != -1) {
                    out.write(buf, 0, count);
                }
            } catch (IOException e) {
                // ignore
            }
            return new Response(code, out, headers);
        }

        throw new IllegalStateException("Both mockMVC and serverUrl are null");
    }

    public static class Request {

        private final HttpMethod method;
        private final String path;

        private String body = "";
        private Map<String, String> params = new HashMap<>();
        private Map<String, String> headers = new HashMap<>();

        public Request(HttpMethod method, String path) {
            this.method = method;
            this.path = path;
        }

        public Request param(String name, String value) {
            params.put(name, value);
            return this;
        }

        public Request header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public Request body(String body) {
            this.body = body != null ? body : "";
            return this;
        }

        public String getParamStr() {
            String str = "";
            boolean firstParam = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (firstParam)
                    firstParam = false;
                else
                    str += "&";
                str += entry.getKey() + "=" + entry.getValue();
            }
            return str;
        }
    }

    public static class Response {

        public final int statusCode;
        private ByteArrayOutputStream content;
        private Map<String, String> headers;

        public Response(int statusCode, ByteArrayOutputStream content, Map<String, String> headers) {
            this.statusCode = statusCode;
            this.content = content;
            this.headers = headers;
        }

        public String getHeader(String header) {
            return headers.get(header == null ? null : header.toLowerCase());
        }

        public byte[] getContentAsBytes() {
            return content.toByteArray();
        }

        public String getContentAsString() {
            return content.toString();
        }
    }
}
