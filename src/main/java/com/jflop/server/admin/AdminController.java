package com.jflop.server.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.springframework.web.bind.annotation.RequestMethod.*;

/**
 * REST API for agents administration
 *
 * @author artem
 *         Date: 7/2/16
 */

@RestController
@RequestMapping(path = AdminController.AGENTS_PATH)
public class AdminController {

    public static final String AGENTS_PATH = "/agents";

    public static final String JFSERVER_PROPERTIES_FILE = "jfserver.properties";
    private static final String JFLOP_AGENT_JAR = "jflop-agent.jar";

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private AdminDAO dao;

    @RequestMapping(method = GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity getAgents() {
        return ResponseEntity.ok(dao.getAgents(accountId()));
    }

    @RequestMapping(method = POST, produces = "application/json")
    @ResponseBody
    public ResponseEntity createAgent(@RequestParam("name") String name) throws URISyntaxException {
        JFAgent agent = dao.createAgent(accountId(), name);
        return ResponseEntity.created(new URI(request.getRequestURI() + "/" + agent.id)).build();
    }

    @RequestMapping(method = PUT, path = "/{id}", produces = "application/json")
    @ResponseBody
    public ResponseEntity updateAgent(@PathVariable("id") String agentId, @RequestParam("name") String name) {
        try {
            dao.updateAgent(accountId(), agentId, name);
            return ResponseEntity.ok().build();
        } catch (NullPointerException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @RequestMapping(method = DELETE, path = "/{id}", produces = "application/json")
    @ResponseBody
    public ResponseEntity deleteAgent(@PathVariable("id") String agentId) {
        try {
            dao.deleteAgent(accountId(), agentId);
            return ResponseEntity.ok().build();
        } catch (NullPointerException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @RequestMapping(method = GET, path = "/{id}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseBody
    public ResponseEntity downloadAgent(@PathVariable("id") String agentId) {
        try {
            InputStream originalJar = new ClassPathResource(JFLOP_AGENT_JAR).getInputStream();
            return ResponseEntity.ok(generateAgentJar(request.getContextPath(), agentId, originalJar));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e);
        }
    }

    private String accountId() {
        return (String) request.getAttribute(AdminSecurityInterceptor.ACCOUNT_ID_ATTRIBUTE);
    }

    private byte[] generateAgentJar(String serverUrl, String agentId, InputStream originalJar) throws IOException {
        // 1. create input and output zip streams
        ZipInputStream in = new ZipInputStream(originalJar);
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ZipOutputStream out = new ZipOutputStream(outBytes);

        // 2. write an entry for configuration properties
        Properties properties = new Properties();
        properties.setProperty("agent.id", agentId);
        properties.setProperty("server.url", serverUrl);
        properties.setProperty("timeout.connectSec", "1");
        properties.setProperty("timeout.readSec", "1");
        properties.setProperty("report.intervalSec", "1");
        ByteArrayOutputStream propertiesBytes = new ByteArrayOutputStream();
        properties.store(propertiesBytes, "auto generated");
        byte[] data = propertiesBytes.toByteArray();
        ZipEntry entry = new ZipEntry(JFSERVER_PROPERTIES_FILE);
        out.putNextEntry(entry);
        out.write(data);
        out.closeEntry();

        // 3. copy entries from the orioginal archive to the output
        while ((entry = in.getNextEntry()) != null) {
            out.putNextEntry(new ZipEntry(entry.getName()));
            out.write(readEntryContent(in));
            out.closeEntry();
        }

        // close the zip and return the bytes
        out.close();
        return outBytes.toByteArray();
    }

    static byte[] readEntryContent(ZipInputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buff = new byte[512];
        int count;
        while ((count = in.read(buff, 0, buff.length)) > 0) {
            out.write(buff, 0, count);
        }
        return out.toByteArray();
    }

}
