package com.jflop.server.feature;

import com.jflop.server.admin.AdminDAO;
import com.jflop.server.admin.ValidationException;
import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.runtime.data.AgentData;
import com.jflop.server.runtime.data.AgentDataFactory;
import com.jflop.server.runtime.data.InstrumentationMetadata;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private AdminDAO adminDAO;

    public ClassInfoFeature() {
        super(FEATURE_NAME);
    }

    public void getDeclaredMethods(AgentJVM agentJVM, Map<String, List<String>> classMethods) {
        // if there already is a command in progress - return
        FeatureCommand currentCommand = adminDAO.getCurrentCommand(agentJVM, FEATURE_NAME);
        if (currentCommand != null && currentCommand.respondedAt == null)
            return;

        // create and send a new command
        FeatureCommand command = new FeatureCommand(FEATURE_NAME, GET_DECLARED_METHODS, classMethods);
        adminDAO.setCommand(agentJVM, FEATURE_NAME, command);
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
            metadata.init(entry.getKey(), entry.getValue());
            res.add(metadata);
        }

        return res;
    }
}
