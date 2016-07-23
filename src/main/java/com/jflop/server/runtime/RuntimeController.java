package com.jflop.server.runtime;

import com.jflop.server.admin.AdminDAO;
import com.jflop.server.admin.JFAgent;
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
    private AdminDAO adminDAO;

    @RequestMapping(method = POST, path = "/{agentId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reportFeaturesData(@PathVariable("agentId") String agentId, @RequestBody Map<String, Object> featuresData) {
        JFAgent agent = adminDAO.getAgent(agentId);
        Map<String, Object> res = new HashMap<>();
        res.put("tasks", agent.reportFeaturesAndGetTasks(featuresData));
        return ResponseEntity.ok(res);
    }
}
