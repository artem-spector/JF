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

    private List<JFAgent> agents = new ArrayList<>();

    public List<JFAgent> getAgents() {
        return agents;
    }
}
