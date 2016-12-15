package com.jflop.server.feature;

import com.jflop.server.admin.ValidationException;
import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.runtime.data.AgentData;
import com.jflop.server.runtime.data.AgentDataFactory;
import com.jflop.server.runtime.data.InstrumentationMetadata;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Provides access to the information about loaded classes
 *
 * @author artem on 12/11/16.
 */
@Component
public class ClassInfoFeature extends AgentFeature {

    public static final String FEATURE_NAME = "classInfo";
    public static final String GET_DECLARED_METHODS = "getDeclaredMethods";

    public ClassInfoFeature() {
        super(FEATURE_NAME);
    }

    public void getDeclaredMethods(AgentJVM agentJVM, Map<String, List<String>> classMethods) {
        sendCommandIfNotInProgress(agentJVM, GET_DECLARED_METHODS, classMethods);
    }

    @Override
    public FeatureCommand parseCommand(AgentJVM agentJVM, String command, String paramStr) throws ValidationException {
        throw new RuntimeException("This method should not be called");
    }

    @Override
    public List<AgentData> parseReportedData(Object dataJson, FeatureCommand command, AgentDataFactory agentDataFactory) {
        List<AgentData> res = new ArrayList<>();

        Map<String, Map<String, List<String>>> classMethodSignatures = (Map<String, Map<String, List<String>>>) dataJson;
        for (Map.Entry<String, Map<String, List<String>>> entry : classMethodSignatures.entrySet()) {
            InstrumentationMetadata metadata = agentDataFactory.createInstance(InstrumentationMetadata.class);
            metadata.setMethodSignatures(entry.getKey(), entry.getValue());
            res.add(metadata);
        }

        return res;
    }
}
