package com.jflop.server.runtime.data;

import com.jflop.server.admin.data.AgentJVM;

import java.security.MessageDigest;
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

        MessageDigest digest = initDigest();
        addStringToDigest(className, digest);
        classId = digestToString(digest);
    }

    @Override
    public String getDocumentId() {
        return classId;
    }
}
