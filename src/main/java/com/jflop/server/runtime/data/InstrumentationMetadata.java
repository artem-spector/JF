package com.jflop.server.runtime.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.util.DigestUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains instrumentation data (method signature and other) retrieved from the agent
 * for a specific method identified by stacktrace element
 *
 * @author artem on 12/11/16.
 */
public class InstrumentationMetadata extends Metadata {

    public String classId;
    public String className;
    public boolean isBlacklisted;
    public String blacklistReason;
    public Map<String, List<String>> methodSignatures;

    public InstrumentationMetadata() {
    }

    public InstrumentationMetadata(AgentJVM agentJVM, String className) {
        this.agentJvm = agentJVM;
        setMethodSignatures(className, null);
    }

    @JsonIgnore
    public void setMethodSignatures(String className, Map<String, List<String>> methodsSignatures) {
        this.className = className;
        this.methodSignatures = methodsSignatures;
        classId = DigestUtil.uniqueId(agentJvm, className);
    }

    public void blacklistClass(String className, String reason) {
        this.className = className;
        this.isBlacklisted = true;
        this.blacklistReason = reason;
        classId = DigestUtil.uniqueId(agentJvm, className);
    }

    @Override
    public String getDocumentId() {
        return classId;
    }

    @Override
    public boolean mergeTo(Metadata existing) {
        InstrumentationMetadata that = (InstrumentationMetadata) existing;
        if (isBlacklisted) {
            that.isBlacklisted = true;
            that.methodSignatures = null;
            that.blacklistReason = blacklistReason;
        } else {
            if (methodSignatures != null && !methodSignatures.isEmpty()) {
                if (that.methodSignatures == null) that.methodSignatures = new HashMap<>();
                that.methodSignatures.putAll(methodSignatures);
            }
        }
        return true;
    }
}
