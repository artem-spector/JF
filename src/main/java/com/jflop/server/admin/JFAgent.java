package com.jflop.server.admin;

import java.util.UUID;

/**
 * JF Agent registration record
 *
 * @author artem
 *         Date: 7/2/16
 */
public class JFAgent {

    public String id;
    public String name;

    @SuppressWarnings("unused") // for JSON deserialization
    public JFAgent() {
    }

    public JFAgent(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }
}
