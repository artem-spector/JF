package com.jflop.server.rest.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.rest.admin.data.AccountData;
import com.jflop.server.rest.admin.data.AgentJVM;
import com.jflop.server.rest.admin.data.AgentJvmState;
import com.jflop.server.rest.admin.data.JFAgent;
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

    public JFAgent createAgent(String accountId, String agentName) {
        JFAgent agent = new JFAgent();
        String str = UUID.randomUUID().toString();
        agent.agentId = str.replace("-", "");
        agent.agentName = agentName;

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
                        Map jvmJson = mapper.readValue(bytes, Map.class);
                        // remove jvm key
                        jvmJson.remove("agentJvm");
                        jvms.put(jvm.agentJvm.jvmId, jvmJson);
                    }
                }
            }

            return res;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public AgentJVM verifyAgentJvm(String agentId, String jvmId) {
        // Validate the agent ID, this is the only authorization check available for agent clients
        // This approach is not scalable.
        // TODO: replace the DB access with a token that contains account ID, and is obtained by agent on JVM start
        AccountData account = accountIndex.findByAgent(agentId);
        if (account == null) throw new RuntimeException("Invalid agent ID: " + agentId);

        AgentJVM agentJvm = new AgentJVM(account.accountId, agentId, jvmId);
        agentJvmIndex.updateJvmState(agentJvm, 3, jvm -> {
            jvm.lastReportedAt = new Date();
        });

        return agentJvm;
    }

}
