package com.jflop.server.rest.runtime;

import com.jflop.server.data.AgentJVM;
import com.jflop.server.rest.admin.AdminDAO;
import com.jflop.server.rest.runtime.kafka.CommandTopicConsumer;
import com.jflop.server.rest.runtime.kafka.InTopicProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    @Autowired
    private InTopicProducer producer;

    @Autowired
    private CommandTopicConsumer consumer;

    @RequestMapping(method = POST, path = "/{agentId}/{jvmId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reportFeaturesData(
            @PathVariable("agentId") String agentId,
            @PathVariable("jvmId") String jvmId,
            @RequestBody Map<String, Object> featuresData) {

        Map<String, Object> res = new HashMap<>();
        try {
            AgentJVM agentJVM = adminDAO.verifyAgentJvm(agentId, jvmId);

            // If agent reported errors, the request contains no data
            logger.info("Request from (" + jvmId + "): " + featuresData);
            List<String> errors = (List<String>) featuresData.remove("errors");
            if (errors != null) {
                logger.severe(errors + " reported by " + agentJVM);
                return ResponseEntity.ok(res);
            }

            // send incoming data to processing
            producer.send(agentJVM, featuresData);

            // receive commands produced by processors
            List<Map> featureCommands = consumer.getFeatureCommands(agentJVM);

            // copy commands to response
            if (featureCommands != null && !featureCommands.isEmpty()) {
                res.put("tasks", featureCommands);
            }

            logger.info("Response to (" + jvmId + "): " + res);
            // always return success
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected exception", e);
            return ResponseEntity.ok(res);
        }
    }

    @ExceptionHandler({Throwable.class})
    public void unexpectedException(Throwable ex) {
        logger.log(Level.SEVERE, "Unexpected exception", ex);
    }

}
