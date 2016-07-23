package com.jflop.server.admin;

import java.util.ArrayList;
import java.util.List;

/**
 * Data for a user account
 *
 * @author artem
 *         Date: 7/2/16
 */
public class AccountData {

    private List<String> agentIDs = new ArrayList<>();

    public List<String> getAgentIDs() {
        return agentIDs;
    }
}
