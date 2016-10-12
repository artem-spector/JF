package com.jflop.server.runtime.data;

import com.jflop.server.admin.data.AgentJVM;

import java.util.Date;

/**
 * Base class containing the field common for all types of raw feature data
 *
 * @author artem
 *         Date: 10/12/16
 */
public abstract class RawFeatureData {

    public AgentJVM agentJvm;
    public Date time;
}
