package com.jflop.server.runtime;

import com.jflop.server.persistency.PersistentData;
import com.jflop.server.admin.AccountIndex;
import com.jflop.server.admin.AgentJVMIndex;
import com.jflop.server.admin.data.*;
import com.jflop.server.feature.FeatureManager;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/24/16
 */
@Component
public class RuntimeDAO {

    @Autowired
    private FeatureManager featureManager;

    @Autowired
    private AccountIndex accountIndex;

    @Autowired
    private AgentJVMIndex agentJVMIndex;

    public List<Map<String, Object>> reportFeaturesData(String agentId, String jvmId, Map<String, Object> featuresData) {
        AccountData account = accountIndex.findByAgent(agentId);
        if (account == null) throw new RuntimeException("Invalid agent ID");

        // update state
        Date now = new Date();
        AgentJVM agentJVM = new AgentJVM(account.accountId, agentId, jvmId);
        PersistentData<AgentJvmState> jvmState = agentJVMIndex.getAgentJvmState(agentJVM, true);
        jvmState.source.lastReportedAt = now;
        jvmState.source.errors = (List<String>) featuresData.remove("errors");

        JFAgent agent = account.getAgent(agentId);
        for (Map.Entry<String, Object> entry : featuresData.entrySet()) {
            String featureId = entry.getKey();
            validateFeature(agent, featureId);

            FeatureCommand command = jvmState.source.getCommand(featureId);
            if (command == null) {
                command = new FeatureCommand();
                command.featureId = featureId;
                jvmState.source.setCommand(command);
            }
            command.respondedAt = now;
            featureManager.getFeature(featureId).updateFeatureState(command, entry.getValue());
        }

        // collect commands to send
        List<Map<String, Object>> taskList = new ArrayList<>();
        for (FeatureCommand command : jvmState.source.featureCommands) {
            if (command.sentAt == null) {
                Map<String, Object> task = new HashMap<>();
                task.put("feature", command.featureId);
                Map<String, Object> commandJson = new HashMap<>();
                task.put("command", commandJson);
                commandJson.put(command.commandName, command.commandParam);
                taskList.add(task);
                command.sentAt = now;
            }
        }

        // update persistent state
        try {
            agentJVMIndex.updateDocument(jvmState);
        } catch (VersionConflictEngineException e) {
            // admin client has interfered, skip this time
            taskList = new ArrayList<>();
        }

        return taskList;
    }

    private void validateFeature(JFAgent agent, String featureId) {
        for (String enabledFeature : agent.enabledFeatures) {
            if (enabledFeature.equals(featureId)) return;
        }
        throw new RuntimeException("Invalid feature ID");
    }
}
