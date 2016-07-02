package com.jflop.server.admin;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin data access object
 *
 * @author artem
 *         Date: 7/2/16
 */
@Component
public class AdminDAO {

    public List<JFAgent> getAgents(String accountId) {
        return new ArrayList<>();
    }
}
