package com.jflop.server.runtime;

import com.jflop.server.take2.admin.AdminDAO;
import com.jflop.server.take2.admin.data.AgentJVM;
import com.jflop.server.take2.admin.data.JFAgent;
import com.jflop.server.take2.runtime.RuntimeDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

/**
 * Accepts runtime requests from the active agents
 *
 * @author artem
 *         Date: 7/16/16
 */
@RestController
@RequestMapping(path = RuntimeController.RUNTIME_API_PATH)
public class RuntimeController {

    public static final String RUNTIME_API_PATH = "/rt";

    @Autowired
    private RuntimeDAO runtimeDAO;

    @RequestMapping(method = POST, path = "/{agentId}/{jvmId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reportFeaturesData(
            @PathVariable("agentId") String agentId,
            @PathVariable("jvmId") String jvmId,
            @RequestBody Map<String, Object> featuresData) {

        Map<String, Object> res = runtimeDAO.reportFeaturesData(agentId, jvmId, featuresData);
        return ResponseEntity.ok(res);
    }
}
