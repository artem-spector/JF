package com.jflop.server.runtime.data;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Base class for occurrence data types that have an ID of their metadata
 *
 * @author artem on 21/12/2016.
 */
public abstract class OccurrenceData extends AgentData {

    @JsonIgnore
    public abstract String getMetadataId();
}
