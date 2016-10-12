package com.jflop.server.runtime;

import com.jflop.server.feature.AgentFeature;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.admin.AccountIndex;
import com.jflop.server.admin.AgentJVMIndex;
import com.jflop.server.admin.data.*;
import com.jflop.server.feature.FeatureManager;
import com.jflop.server.runtime.data.RawFeatureData;
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

    @Autowired
    private RawDataIndex rawDataIndex;

    public List<Map<String, Object>> reportFeaturesData(String agentId, String jvmId, Map<String, Object> featuresData) {
        // Validate the agent ID, this is the only authorization check available for agent clients
        // This approach is not scalable.
        // TODO: replace the DB access with a token that contains account ID, and is obtained by agent on JVM start
        AccountData account = accountIndex.findByAgent(agentId);
        if (account == null) throw new RuntimeException("Invalid agent ID");

        // update JVM - level state
        Date now = new Date();
        AgentJVM agentJvm = new AgentJVM(account.accountId, agentId, jvmId);
        PersistentData<AgentJvmState> jvmState = agentJVMIndex.getAgentJvmState(agentJvm, true);
        jvmState.source.lastReportedAt = now;
        jvmState.source.errors = (List<String>) featuresData.remove("errors");

        // loop by features reported by the agent, and update the command state and insert the raw data
        JFAgent agent = account.getAgent(agentId);
        for (Map.Entry<String, Object> entry : featuresData.entrySet()) {
            // make sure the reported feature is enabled for the agent
            String featureId = entry.getKey();
            validateFeature(agent, featureId);
            AgentFeature feature = featureManager.getFeature(featureId);

            // get or create the feature command
            FeatureCommand command = jvmState.source.getCommand(featureId);
            if (command == null) {
                command = new FeatureCommand();
                command.featureId = featureId;
                jvmState.source.setCommand(command);
            }

            // update the command state and extract raw data
            command.respondedAt = now;
            Object dataJson = entry.getValue();
            RawFeatureData rawData = feature.parseReportedData(dataJson, command);

            // insert raw data
            // TODO: use bulk update instead of inserting one by one
            if (rawData != null) {
                rawData.agentJvm = agentJvm;
                rawData.time = now;
                rawDataIndex.createDocument(new PersistentData<>(rawData));
            }
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
