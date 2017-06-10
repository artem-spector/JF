package com.jflop.server.rest.runtime;

import com.jflop.server.rest.admin.AdminDAO;
import com.jflop.server.data.AgentJVM;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Collections.EMPTY_MAP;
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

    private static final Logger logger = Logger.getLogger(RuntimeController.class.getName());

    public static final String RUNTIME_API_PATH = "/rt";

    @Autowired
    private AdminDAO adminDAO;

    @RequestMapping(method = POST, path = "/{agentId}/{jvmId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reportFeaturesData(
            @PathVariable("agentId") String agentId,
            @PathVariable("jvmId") String jvmId,
            @RequestBody Map<String, Object> featuresData) {

        try {
            AgentJVM agentJVM = adminDAO.verifyAgentJvm(agentId, jvmId);

            // TODO: send the data to Kafka producer, retrieve the commands from Kafka consumer
            Map tasks = EMPTY_MAP;

            Map<String, Object> res = new HashMap<>();
            if (!tasks.isEmpty()) {
                res.put("tasks", tasks);
            }
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected exception", e);
            return ResponseEntity.ok(null);
        }
    }

    @ExceptionHandler({Throwable.class})
    public void unexpectedException(Throwable ex) {
    }
}
