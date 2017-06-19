package com.jflop.server.rest.runtime;

import com.jflop.server.data.AgentJVM;
import com.jflop.server.rest.admin.AdminDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.jflop.server.TopicNames.COMMAND_OUT_TOPIC;
import static com.jflop.server.TopicNames.IN_TOPIC;
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

    private KafkaTopicProducer producer;
    private KafkaTopicConsumer consumer;

    @RequestMapping(method = POST, path = "/{agentId}/{jvmId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reportFeaturesData(
            @PathVariable("agentId") String agentId,
            @PathVariable("jvmId") String jvmId,
            @RequestBody Map<String, Object> featuresData) {

        try {
            AgentJVM agentJVM = adminDAO.verifyAgentJvm(agentId, jvmId);

            // send incoming data to processing
            KafkaTopicProducer producer = getProducer();
            if (producer != null) producer.send(agentJVM, featuresData);

            // receive commands produced by processors
            List<Map<String, Object>> featureCommands = null;
            KafkaTopicConsumer consumer = getConsumer();
            if (consumer != null) {
                featureCommands = consumer.getFeatureCommands(agentJVM);
            }

            // copy commands to response
            Map<String, Object> res = new HashMap<>();
            if (featureCommands != null && !featureCommands.isEmpty()) {
                res.put("tasks", featureCommands);
            }

            // always return success
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected exception", e);
            return ResponseEntity.ok(null);
        }
    }

    @ExceptionHandler({Throwable.class})
    public void unexpectedException(Throwable ex) {
        logger.log(Level.SEVERE, "Unexpected exception", ex);
    }

    private KafkaTopicProducer getProducer() {
        if (producer == null) {
            try {
                producer = new KafkaTopicProducer(IN_TOPIC);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed creating Kafka producer", e);
            }
        }
        return producer;
    }

    private KafkaTopicConsumer getConsumer() {
        if (consumer == null) {
            try {
                consumer = new KafkaTopicConsumer(COMMAND_OUT_TOPIC, "jf-rest-server");
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed creating Kafka consumer", e);
            }
        }
        return consumer;
    }
}
