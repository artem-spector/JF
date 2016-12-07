package com.jflop.server.runtime.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jflop.server.admin.data.AgentJVM;

import java.util.Date;

/**
 * Base class for any data type coming from an agent.
 *
 * @author artem
 *         Date: 10/12/16
 */
public abstract class AgentData {

    @JsonProperty
    protected AgentJVM agentJvm;

    @JsonProperty
    protected Date time;

    @JsonProperty
    protected String dataType;

}
