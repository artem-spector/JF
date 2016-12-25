package com.jflop.server.background;

import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.feature.ClassInfoFeature;
import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.feature.SnapshotFeature;
import com.jflop.server.persistency.ValuePair;
import com.jflop.server.runtime.MetadataIndex;
import com.jflop.server.runtime.RawDataIndex;
import com.jflop.server.runtime.data.*;
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

    // step-level state
    private AgentJVM agentJvm;
    Date from;
    Date to;
    Map<ThreadMetadata, List<ThreadOccurrenceData>> threads;
    Map<FlowMetadata, List<FlowOccurenceData>> flows;
    Map<ThreadMetadata, List<FlowMetadata>> threadsToFlows;
    Set<MethodConfiguration> methodsToInstrument;
    private JflopConfiguration currentInstrumentation;

    public JvmMonitorAnalysis() {
        super("JVMRawDataAnalysis", 60, 3, 100);
    }

    @Override
    public void step(TaskLockData lock, Date refreshThreshold) {
        beforeStep(lock, refreshThreshold);
        analyze();
        takeSnapshot();
        afterStep(lock);
    }

    void beforeStep(TaskLockData lock, Date refreshThreshold) {
        threads = null;
        flows = null;
        threadsToFlows = null;
        methodsToInstrument = null;
        currentInstrumentation = null;

        agentJvm = lock.agentJvm;
        from = lock.processedUntil;
        this.to = refreshThreshold;
    }

    void afterStep(TaskLockData lock) {
        lock.processedUntil = to;
    }

    void analyze() {
        mapThreadsToFlows();
        instrumentUncoveredThreads();
        adjustInstrumentation();
    }

    void takeSnapshot() {
        if (currentInstrumentation != null)
            snapshotFeature.takeSnapshot(agentJvm, 1);
    }

    void adjustInstrumentation() {
        JflopConfiguration current = instrumentationConfigurationFeature.getConfiguration(agentJvm);
        if (current == null) return;
        ArrayList<MethodConfiguration> currentMethods = new ArrayList<>(current.getAllMethods());

        for (MethodConfiguration mtd : methodsToInstrument) current.addMethodConfig(mtd);
        Set<String> blacklist = metadataIndex.getBlacklistedClasses(agentJvm);
        for (String className : blacklist)
            current.removeClass(NameUtils.getInternalClassName(className));

        if (!current.getAllMethods().equals(currentMethods)) {
            instrumentationConfigurationFeature.setConfiguration(agentJvm, current);
            return;
        }

        currentInstrumentation = current;
    }

    void instrumentUncoveredThreads() {
        if (threads == null) return;

        Map<String, InstrumentationMetadata> classMetadataCache = new HashMap<>();
        Map<String, List<String>> missingSignatures = new HashMap<>();

        // build a set of class-method-signature for all instrumentable methods of uncovered threads
        Set<ThreadMetadata> uncoveredThreads = new HashSet<>(threads.keySet());
        if (threadsToFlows != null) uncoveredThreads.removeAll(threadsToFlows.keySet());
        methodsToInstrument = new HashSet<>();
        for (ThreadMetadata thread : uncoveredThreads) {
            for (ValuePair<String, String> pair : thread.getInstrumentableMethods()) {
                String className = pair.value1;
                String methodName = pair.value2;

                InstrumentationMetadata classMetadata = classMetadataCache.computeIfAbsent(className, k -> metadataIndex.getClassMetadata(agentJvm, className));
                List<String> signatures = classMetadata == null ? null
                        : classMetadata.isBlacklisted ? Collections.EMPTY_LIST
                        : classMetadata.methodSignatures.get(methodName);
                if (signatures != null) {
                    if (signatures.size() > 1)
                        logger.warning(signatures.size() + " signatures found for method " + className + "#" + methodName + ", instrumenting all of them.");
                    for (String signature : signatures) {
                        methodsToInstrument.add(new MethodConfiguration(className + "." + signature));
                    }
                } else {
                    String internalClassName = NameUtils.getInternalClassName(className); // need it because ES does not like "." in field names (map keys)
                    List<String> methods = missingSignatures.computeIfAbsent(internalClassName, k -> new ArrayList<>());
                    methods.add(methodName);
                }
            }
        }

        // if some signatures are unknown, request the class metadata
        if (!missingSignatures.isEmpty()) {
            classInfoFeature.getDeclaredMethods(agentJvm, missingSignatures);
        }
    }

    void mapThreadsToFlows() {
        // 1. get recent threads and their metadata
        threads = rawDataIndex.getOccurrencesAndMetadata(agentJvm, ThreadOccurrenceData.class, ThreadMetadata.class, from, to);
        if (threads == null || threads.isEmpty()) return;

        // 2. get recent snapshots and their metadata
        threadsToFlows = new HashMap<>();
        flows = rawDataIndex.getOccurrencesAndMetadata(agentJvm, FlowOccurenceData.class, FlowMetadata.class, from, to);
        if (flows == null || flows.isEmpty()) return;

        // 3. find out which thread dumps represent what flows
        for (ThreadMetadata thread : threads.keySet()) {
            List<FlowMetadata.FlowElement> expectedFlow = thread.getFlowElements();
            for (FlowMetadata flow : flows.keySet()) {
                if (flow.fitsExpectedFlow(expectedFlow)) {
                    threadsToFlows.computeIfAbsent(thread, threadMetadata -> new ArrayList<>()).add(flow);
                }
            }
        }
    }
}
