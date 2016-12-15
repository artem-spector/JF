package com.jflop.server.background;

import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.feature.ClassInfoFeature;
import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.feature.SnapshotFeature;
import com.jflop.server.persistency.ValuePair;
import com.jflop.server.runtime.MetadataIndex;
import com.jflop.server.runtime.RawDataIndex;
import com.jflop.server.runtime.data.InstrumentationMetadata;
import org.jflop.config.JflopConfiguration;
import org.jflop.config.MethodConfiguration;
import org.jflop.config.NameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.logging.Logger;

/**
 * Analyze raw data produced by {@link com.jflop.server.feature.JvmMonitorFeature}
 *
 * @author artem on 12/8/16.
 */
@Component
public class JvmMonitorAnalysis extends BackgroundTask {

    private static final Logger logger = Logger.getLogger(JvmMonitorAnalysis.class.getName());

    @Autowired
    private RawDataIndex rawDataIndex;

    @Autowired
    private MetadataIndex metadataIndex;

    @Autowired
    private ClassInfoFeature classInfoFeature;

    @Autowired
    private InstrumentationConfigurationFeature instrumentationConfigurationFeature;

    @Autowired
    private SnapshotFeature snapshotFeature;

    public JvmMonitorAnalysis() {
        super("JVMRawDataAnalysis", 60, 3, 100);
    }

    @Override
    public void step(TaskLockData lock) {
        instrumentActiveThreads(lock.agentJvm);
    }

    private void instrumentActiveThreads(AgentJVM agentJvm) {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.add(Calendar.MINUTE, -5);
        Set<String> recentDumpIds = rawDataIndex.getRecentDumpIds(agentJvm, calendar.getTime());
        Set<ValuePair<String, String>> instrumentable = metadataIndex.getInstrumentableMethods(recentDumpIds);

        // get the method signatures, and collect the methods with missing signatures
        Map<String, InstrumentationMetadata> classMetadataCache = new HashMap<>();
        Map<String, Map<String, List<String>>> classMethodSignatures = new HashMap<>();
        Map<String, List<String>> missingSignatures = new HashMap<>();

        for (ValuePair<String, String> pair : instrumentable) {
            String className = pair.value1;
            String methodName = pair.value2;

            InstrumentationMetadata classMetadata = classMetadataCache.computeIfAbsent(className, k -> metadataIndex.getClassMetadata(agentJvm, className));
            List<String> signatures = classMetadata == null ? null
                    : classMetadata.isBlacklisted ? Collections.EMPTY_LIST
                    : classMetadata.methodSignatures.get(methodName);
            if (signatures != null) {
                Map<String, List<String>> methodSignatures = classMethodSignatures.computeIfAbsent(className, k -> new HashMap<>());
                methodSignatures.put(methodName, signatures);
            } else {
                String internalClassName = NameUtils.getInternalClassName(className); // need it because ES does not like "." in field names (map keys)
                List<String> methods = missingSignatures.computeIfAbsent(internalClassName, k -> new ArrayList<>());
                methods.add(methodName);
            }
        }

        if (!missingSignatures.isEmpty()) {
            classInfoFeature.getDeclaredMethods(agentJvm, missingSignatures);
            return;
        }

        if (!classMethodSignatures.isEmpty()) {
            JflopConfiguration conf = new JflopConfiguration();

            for (Map.Entry<String, Map<String, List<String>>> entry : classMethodSignatures.entrySet()) {
                String className = entry.getKey();
                for (Map.Entry<String, List<String>> methodEntry : entry.getValue().entrySet()) {
                    String methodName = methodEntry.getKey();
                    List<String> signatures = methodEntry.getValue();
                    switch (signatures.size()) {
                        case 0:
                            // the method is not instrumentable, don't add it
                            break;
                        case 1:
                            conf.addMethodConfig(new MethodConfiguration(className + "." + signatures.get(0)));
                            break;
                        default:
                            logger.warning(signatures.size() + " signatures found for method " + className + "#" + methodName + ", instrumenting all of them.");
                            for (String signature : signatures) {
                                conf.addMethodConfig(new MethodConfiguration(className + "." + signature));
                            }
                            break;
                    }
                }
            }

            JflopConfiguration existing = instrumentationConfigurationFeature.getConfiguration(agentJvm);
            if (existing == null)
                return;

            Set<MethodConfiguration> methodsToAdd = new HashSet<>(conf.getAllMethods());
            methodsToAdd.removeAll(existing.getAllMethods());
            if (!methodsToAdd.isEmpty()) {
                for (MethodConfiguration methodConfig : methodsToAdd) {
                    existing.addMethodConfig(methodConfig);
                }
                instrumentationConfigurationFeature.setConfiguration(agentJvm, existing);
                return;
            }

            snapshotFeature.takeSnapshot(agentJvm, 1);
        }
    }
}
