package com.jflop.server.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.admin.data.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/17/16
 */
@Component
public class AdminDAO {

    private ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private AccountIndex accountIndex;

    @Autowired
    private AgentJVMIndex agentJvmIndex;

    public AccountData createAccount(String accountName) {
        return accountIndex.createAccount(accountName);
    }

    public void deleteAccount(String accountId) {
        accountIndex.deleteAccount(accountId);
        agentJvmIndex.deleteAccount(accountId);
    }

    public AccountData findAccountByName(String accountName) {
        return accountIndex.findByName(accountName);
    }

    public JFAgent createAgent(String accountId, String agentName, String[] features) {
        JFAgent agent = new JFAgent();
        String str = UUID.randomUUID().toString();
        agent.agentId = str.replace("-", "");
        agent.agentName = agentName;
        agent.enabledFeatures = features;

        accountIndex.addAgent(accountId, agent);
        return agent;
    }

    public void updateAgent(String accountId, String agentId, String agentName) {
        accountIndex.updateAgent(accountId, agentId, agentName);
    }

    public void deleteAgent(String accountId, String agentId) {
        accountIndex.deleteAgent(accountId, agentId);
        agentJvmIndex.deleteAgent(accountId, agentId);
    }

    public List<Map<String, Object>> getAccountAgentsJson(String accountId) {
        List<Map<String, Object>> res = new ArrayList<>();

        AccountData account = accountIndex.getAccount(accountId);
        List<AgentJvmState> agentJvms = agentJvmIndex.getAgentJvms(accountId);

        try {
            for (JFAgent agent : account.agents) {
                byte[] bytes = mapper.writeValueAsBytes(agent);
                Map<String, Object> agentJson = mapper.readValue(bytes, Map.class);
                Map<String, Object>  jvms = new HashMap<>();
                agentJson.put("jvms", jvms);
                res.add(agentJson);
                for (AgentJvmState jvm : agentJvms) {
                    if (jvm.agentJvm.agentId.equals(agent.agentId)) {
                        bytes = mapper.writeValueAsBytes(jvm);
                        Map jvmMap = mapper.readValue(bytes, Map.class);
                        jvmMap.remove("agentJvm");
                        jvms.put(jvm.agentJvm.jvmId, jvmMap);
                    }
                }
            }

            return res;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean setCommand(AgentJVM agentJVM, String featureId, FeatureCommand command) {
        verifyAccount(agentJVM.accountId, agentJVM.agentId, featureId);
        return agentJvmIndex.setCommand(agentJVM, command);
    }

    private AccountData verifyAccount(String accountId, String agentId, String featureId) {
        AccountData account = accountIndex.getAccount(accountId);
        if (account == null) throw new RuntimeException("Invalid account ID");
        if (agentId == null) return account;

        JFAgent agent = account.getAgent(agentId);
        if (agent == null) throw new RuntimeException("Invalid agent ID");
        if (featureId == null) return account;

        for (String feature : agent.enabledFeatures) {
            if (feature.equals(featureId)) return account;
        }
        throw new RuntimeException("Invalid feature ID");
    }

}
