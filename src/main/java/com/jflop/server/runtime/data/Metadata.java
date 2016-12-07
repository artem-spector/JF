package com.jflop.server.runtime.data;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Base class for "metadata" objects, which define their own unique IDs
 *
 * @author artem on 12/7/16.
 */
public abstract class Metadata extends AgentData {

    /**
     * Get the custom document ID.
     * @return document ID, not null
     */
    @JsonIgnore
    public abstract String getDocumentId();
}
