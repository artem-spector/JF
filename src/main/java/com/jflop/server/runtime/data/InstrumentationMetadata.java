package com.jflop.server.runtime.data;

import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.util.DigestUtil;

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
    public Map<String, List<String>> methodSignatures;

    public InstrumentationMetadata() {
    }

    public InstrumentationMetadata(AgentJVM agentJVM, String className) {
        this.agentJvm = agentJVM;
        init(className, null);
    }

    public void init(String className, Map<String, List<String>> methodsSignatures) {
        this.className = className;
        this.methodSignatures = methodsSignatures;
        classId = DigestUtil.uniqueId(agentJvm, className);
    }

    @Override
    public String getDocumentId() {
        return classId;
    }

    @Override
    public boolean mergeTo(Metadata existing) {
        InstrumentationMetadata that = (InstrumentationMetadata) existing;
        that.methodSignatures.putAll(methodSignatures);
        return true;
    }
}
