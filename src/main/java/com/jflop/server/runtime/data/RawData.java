package com.jflop.server.runtime.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jflop.server.admin.data.AgentJVM;

import java.util.Date;

/**
 * Base class containing the field common for all types of raw data
 *
 * @author artem
 *         Date: 10/12/16
 */
public abstract class RawData {

    @JsonProperty
    protected AgentJVM agentJvm;

    @JsonProperty
    protected Date time;

    @JsonProperty
    protected String dataType;

    protected RawData() {
    }

    /**
     * Define the custom document ID. Default implementation does not define ID and returns null
     * @return custom document ID or null
     */
    @JsonIgnore
    public String getDocumentId() {
        return null;
    }
}
