package com.jflop.server.take2.admin.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/17/16
 */
public class AccountData {

    public String accountId;
    public String accountName;
    public List<JFAgent> agents;

    public AccountData() {
    }

    public AccountData(String accountName, String accountId) {
        this.accountName = accountName;
        this.accountId = accountId;
        agents = new ArrayList<>();
    }

    public JFAgent getAgent(String agentId) {
        for (JFAgent agent : agents) {
            if (agent.agentId.equals(agentId)) return agent;
        }
        return null;
    }

    public boolean removeAgent(String agentId) {
        for (Iterator<JFAgent> iter = agents.iterator(); iter.hasNext(); ) {
            if (iter.next().agentId.equals(agentId)) {
                iter.remove();
                return true;
            }
        }
        return false;
    }
}
