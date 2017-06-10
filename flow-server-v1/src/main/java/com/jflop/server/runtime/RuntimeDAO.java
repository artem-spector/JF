package com.jflop.server.runtime;

import com.jflop.server.admin.AccountIndex;
import com.jflop.server.admin.AgentJVMIndex;
import com.jflop.server.admin.data.*;
import com.jflop.server.feature.AgentFeature;
import com.jflop.server.feature.FeatureManager;
import com.jflop.server.persistency.DocType;
import com.jflop.server.runtime.data.AgentData;
import com.jflop.server.runtime.data.AgentDataFactory;
import com.jflop.server.runtime.data.Metadata;
import org.springframework.beans.factory.InitializingBean;
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
public class RuntimeDAO implements InitializingBean {

    @Autowired
    private FeatureManager featureManager;

    @Autowired
    private AccountIndex accountIndex;

    @Autowired
    private AgentJVMIndex agentJVMIndex;

    @Autowired
    private RawDataIndex rawDataIndex;

    @Autowired
    private MetadataIndex metadataIndex;

    private ArrayList<DocType> allDocTypes;

    @Override
    public void afterPropertiesSet() throws Exception {
        allDocTypes = new ArrayList<>();
        allDocTypes.addAll(rawDataIndex.getDocTypes());
        allDocTypes.addAll(metadataIndex.getDocTypes());
    }

    public List<Map<String, Object>> reportFeaturesData(String agentId, String jvmId, Map<String, Object> featuresData) {
        // Validate the agent ID, this is the only authorization check available for agent clients
        // This approach is not scalable.
        // TODO: replace the DB access with a token that contains account ID, and is obtained by agent on JVM start
        AccountData account = accountIndex.findByAgent(agentId);
        if (account == null) throw new RuntimeException("Invalid agent ID: " + agentId);

        Date now = new Date();
        AgentJVM agentJvm = new AgentJVM(account.accountId, agentId, jvmId);
        AgentJvmState jvmState = agentJVMIndex.getAgentJvmState(agentJvm, true).source;

        // loop by features reported by the agent, and update the command state and insert the raw data
        JFAgent agent = account.getAgent(agentId);
        AgentDataFactory agentDataFactory = new AgentDataFactory(agentJvm, now, allDocTypes);
        List<FeatureCommand> updatedCommands = new ArrayList<>();
        for (Map.Entry<String, Object> entry : featuresData.entrySet()) {
            // make sure the reported feature is enabled for the agent
            String featureId = entry.getKey();
            validateFeature(agent, featureId);
            AgentFeature feature = featureManager.getFeature(featureId);

            // get or create the feature command
            FeatureCommand command = jvmState.getCommand(featureId);
            if (command == null) {
                command = new FeatureCommand();
                command.featureId = featureId;
                jvmState.setCommand(command);
            }
            updatedCommands.add(command);

            // update the command state and extract raw data
            command.respondedAt = now;
            List<AgentData> rawData = feature.parseReportedData(entry.getValue(), command, agentDataFactory);
            if (rawData != null) {
                List<Metadata> metadata = new ArrayList<>();
                for (Iterator<AgentData> iterator = rawData.iterator(); iterator.hasNext(); ) {
                    AgentData data = iterator.next();
                    if (data instanceof Metadata) {
                        iterator.remove();
                        metadata.add((Metadata) data);
                    }
                }

                // insert raw data and metadata
                rawDataIndex.addRawData(rawData);
                metadataIndex.addMetadata(metadata);
            }
        }

        agentJVMIndex.updateJvmState(agentJvm, 3, jvm -> {
            jvm.lastReportedAt = now;
            jvm.errors = (List<String>) featuresData.remove("errors");
            for (FeatureCommand command : updatedCommands)
                jvm.setCommand(command);
        });

        return retrieveCommandsToSend(now, agentJvm);
    }

    private List<Map<String, Object>> retrieveCommandsToSend(Date now, AgentJVM agentJvm) {
        List<Map<String, Object>> taskList = new ArrayList<>();
        agentJVMIndex.updateJvmState(agentJvm, 3, jvm -> {
            for (FeatureCommand command : jvm.featureCommands) {
                if (command.sentAt == null) {
                    command.sentAt = now;
                    taskList.add(commandAsJson(command));
                }
            }
        });
        return taskList;
    }

    private Map<String, Object> commandAsJson(FeatureCommand command) {
        Map<String, Object> task = new HashMap<>();
        task.put("feature", command.featureId);
        Map<String, Object> commandJson = new HashMap<>();
        task.put("command", commandJson);
        commandJson.put(command.commandName, command.commandParam);
        return task;
    }

    private void validateFeature(JFAgent agent, String featureId) {
        for (String enabledFeature : agent.enabledFeatures) {
            if (enabledFeature.equals(featureId)) return;
        }
        throw new RuntimeException("Invalid feature ID");
    }
}
