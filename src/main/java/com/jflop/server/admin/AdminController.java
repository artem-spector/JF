package com.jflop.server.admin;

import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.admin.data.JFAgent;
import com.jflop.server.feature.AgentFeature;
import com.jflop.server.feature.FeatureManager;
import com.jflop.server.runtime.RuntimeController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
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
    private AdminDAO adminDAO;

    @Autowired
    private FeatureManager featureManager;

    @RequestMapping(method = GET, produces = "application/json")
    @ResponseBody
    public ResponseEntity getAgents() {
        List<Map<String, Object>> agents = adminDAO.getAccountAgentsJson(accountId());
        return ResponseEntity.ok(agents);
    }

    @RequestMapping(method = POST, produces = "application/json")
    @ResponseBody
    public ResponseEntity createAgent(@RequestParam("name") String name) throws URISyntaxException {
        JFAgent agent = adminDAO.createAgent(accountId(), name, featureManager.getDefaultFeatures());
        return ResponseEntity.created(new URI(request.getRequestURI() + "/" + agent.agentId)).build();
    }

    @RequestMapping(method = PUT, path = "/{id}", produces = "application/json")
    @ResponseBody
    public ResponseEntity updateAgent(@PathVariable("id") String agentId, @RequestParam("name") String name) {
        try {
            adminDAO.updateAgent(accountId(), agentId, name);
            return ResponseEntity.ok().build();
        } catch (NullPointerException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @RequestMapping(method = DELETE, path = "/{id}", produces = "application/json")
    @ResponseBody
    public ResponseEntity deleteAgent(@PathVariable("id") String agentId) {
        try {
            adminDAO.deleteAgent(accountId(), agentId);
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
            String protocol = request.getProtocol().toLowerCase().contains("https") ? "https" : "http";
            String serverUrl = protocol + "://" + request.getLocalName() + ":" + request.getLocalPort();
            if (!request.getContextPath().isEmpty()) serverUrl += "/" + request.getContextPath();
            serverUrl += RuntimeController.RUNTIME_API_PATH;
            return ResponseEntity.ok(generateAgentJar(serverUrl, agentId, originalJar));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e);
        }
    }

    @RequestMapping(method = POST, path = "/{agentId}/{jvmId}/command", produces = "application/json")
    @ResponseBody
    public ResponseEntity command(@PathVariable("agentId") String agentId,
                                  @PathVariable("jvmId") String jvmId,
                                  @RequestParam("feature") String featureId,
                                  @RequestParam("command") String command,
                                  @RequestParam(value = "data", required = false) String data) throws IOException {

        AgentFeature feature = featureManager.getFeature(featureId);
        try {
            FeatureCommand featureCommand = feature.parseCommand(command, data);
            adminDAO.setCommand(new AgentJVM(accountId(), agentId, jvmId), featureId, featureCommand);
            return ResponseEntity.ok().build();
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.toResponseBody());
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
