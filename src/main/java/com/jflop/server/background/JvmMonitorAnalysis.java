package com.jflop.server.background;

import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.feature.ClassInfoFeature;
import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.feature.SnapshotFeature;
import com.jflop.server.runtime.MetadataIndex;
import com.jflop.server.runtime.ProcessedDataIndex;
import com.jflop.server.runtime.RawDataIndex;
import com.jflop.server.runtime.data.*;
import com.jflop.server.runtime.data.processed.FlowSummary;
import com.jflop.server.runtime.data.processed.MethodCall;
import com.jflop.server.runtime.data.processed.MethodFlow;
import com.jflop.server.util.DebugPrintUtil;
import org.jflop.config.JflopConfiguration;
import org.jflop.config.MethodConfiguration;
import org.jflop.config.NameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.logging.Level;
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
    private ProcessedDataIndex processedDataIndex;

    @Autowired
    private ClassInfoFeature classInfoFeature;

    @Autowired
    private InstrumentationConfigurationFeature instrumentationConfigurationFeature;

    @Autowired
    private SnapshotFeature snapshotFeature;

    // step-level state
    static class StepState {
        private AgentJVM agentJvm;
        Date from;
        Date to;
        Map<ThreadMetadata, List<ThreadOccurrenceData>> threads;
        Map<FlowMetadata, List<FlowOccurrenceData>> flows;
        FlowSummary flowSummary;
        Set<MethodConfiguration> methodsToInstrument;
        private JflopConfiguration currentInstrumentation;
        private Set<StackTraceElement> instrumentedTraceElements;
        AgentDataFactory agentDataFactory;
    }

    static ThreadLocal<StepState> step = new ThreadLocal<>();

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
        step.set(new StepState());

        step.get().agentJvm = lock.agentJvm;
        step.get().from = lock.processedUntil;
        step.get().to = refreshThreshold;
        step.get().agentDataFactory = new AgentDataFactory(lock.agentJvm, refreshThreshold, processedDataIndex.getDocTypes());

        step.get().currentInstrumentation = instrumentationConfigurationFeature.getConfiguration(step.get().agentJvm);
        step.get().instrumentedTraceElements = new HashSet<>();
        step.get().methodsToInstrument = new HashSet<>();
    }

    void afterStep(TaskLockData lock) {
        lock.processedUntil = step.get().to;
        step.remove();
    }

    void analyze() {
        mapThreadsToFlows();
        instrumentUncoveredThreads();
        adjustInstrumentation();
    }

    void takeSnapshot() {
        // start with minimal snapshot duration, it will be increased if necessary and possible
        int snapshotDuration = 1;

        StepState current = step.get();
        if (current.flowSummary != null && current.threads != null) {
            snapshotDuration = current.flowSummary.snapshotDurationSec;
            if (snapshotDuration < 5 && needToIncreaseSnapshotDuration(snapshotDuration)) {
                snapshotDuration++;
                logger.fine("Snapshot duration increased to " + snapshotDuration + " sec.");
            } else if (snapshotDuration > 1 && needToDecreaseSnapshotDuration(snapshotDuration)) {
                snapshotDuration--;
                logger.fine("Snapshot duration decreased to " + snapshotDuration + " sec.");
            }
        }

        logger.fine("Taking snapshot (" + snapshotDuration + ")");
        snapshotFeature.takeSnapshot(step.get().agentJvm, snapshotDuration);
    }

    private boolean needToDecreaseSnapshotDuration(float duration) {
        // if all flows happen more than 10 times in a snapshot, may decrease the duration
        return minFlowThroughput() * duration > 10;
    }

    private boolean needToIncreaseSnapshotDuration(float duration) {
        StepState current = step.get();

        // if there are instrumented threads without flows, try to increase the duration
        for (ThreadMetadata thread : current.threads.keySet()) {
            if (current.flowSummary.coversThread(thread.dumpId))
                continue;

            for (StackTraceElement traceElement : thread.stackTrace) {
                if (isInstrumented(traceElement))
                    return true;
            }
        }

        // if some flows happen less than twice in a snapshot - increase the duration
        return minFlowThroughput() * duration < 2;

    }

    private float minFlowThroughput() {
        float res = Float.MAX_VALUE;
        for (MethodCall root : step.get().flowSummary.roots) {
            for (MethodFlow flow : root.flows) {
                res = Math.min(res, flow.statistics.throughputPerSec);
            }
        }
        return res;
    }

    void adjustInstrumentation() {
        JflopConfiguration conf = new JflopConfiguration();
        if (step.get().currentInstrumentation != null)
            step.get().currentInstrumentation.getAllMethods().forEach(conf::addMethodConfig);
        step.get().methodsToInstrument.forEach(conf::addMethodConfig);

        Set<String> blacklist = metadataIndex.getBlacklistedClasses(step.get().agentJvm);
        for (String className : blacklist)
            conf.removeClass(NameUtils.getInternalClassName(className));

        if (!conf.equals(step.get().currentInstrumentation))
            instrumentationConfigurationFeature.setConfiguration(step.get().agentJvm, conf);
    }

    void instrumentUncoveredThreads() {
        if (step.get().threads == null) return;

        Map<String, InstrumentationMetadata> classMetadataCache = new HashMap<>();
        Map<String, Set<String>> missingSignatures = new HashMap<>();

        // find all instrumentable and not instrumented methods
        for (ThreadMetadata thread : step.get().threads.keySet()) {
            for (StackTraceElement traceElement : thread.stackTrace) {
                if (thread.isInstrumentable(traceElement) && !isInstrumented(traceElement)) {
                    String className = traceElement.getClassName();
                    String methodName = traceElement.getMethodName();

                    InstrumentationMetadata classMetadata = classMetadataCache.computeIfAbsent(className, k -> metadataIndex.getClassMetadata(step.get().agentJvm, className));
                    List<String> signatures = classMetadata == null ? null
                            : classMetadata.isBlacklisted ? Collections.EMPTY_LIST
                            : classMetadata.methodSignatures.get(methodName);
                    if (signatures != null) {
                        if (signatures.size() > 1)
                            logger.fine(signatures.size() + " signatures found for method " + className + "#" + methodName + ", instrumenting all of them.");
                        for (String signature : signatures) {
                            step.get().methodsToInstrument.add(new MethodConfiguration(className + "." + signature));
                        }
                    } else {
                        String internalClassName = NameUtils.getInternalClassName(className); // need it because ES does not like "." in field names (map keys)
                        Set<String> methods = missingSignatures.computeIfAbsent(internalClassName, k -> new HashSet<>());
                        methods.add(methodName);
                    }
                }
            }
        }

        logger.fine("Will instrument methods: " + step.get().methodsToInstrument);

        // if some signatures are unknown, request the class metadata
        if (!missingSignatures.isEmpty()) {
            logger.fine("Asking for signatures: " + missingSignatures);
            classInfoFeature.getDeclaredMethods(step.get().agentJvm, missingSignatures);
        }
    }

    void mapThreadsToFlows() {
        // 1. get recent threads and their metadata
        StepState current = step.get();
        current.threads = rawDataIndex.getOccurrencesAndMetadata(current.agentJvm, ThreadOccurrenceData.class, ThreadMetadata.class, current.from, current.to);
        if (current.threads == null || current.threads.isEmpty()) return;
        if (logger.isLoggable(Level.FINE)) logger.fine("Found " + current.threads.size() + " distinct threads");

        // 2. get recent snapshots and their metadata
        current.flows = rawDataIndex.getOccurrencesAndMetadata(current.agentJvm, FlowOccurrenceData.class, FlowMetadata.class, current.from, current.to);
        if (current.flows == null || current.flows.isEmpty()) return;
        if (logger.isLoggable(Level.FINE)) logger.fine("Found " + current.flows.size() + " distinct flows");

        // 3. build flow summary
        FlowSummary flowSummary = current.agentDataFactory.createInstance(FlowSummary.class);
        long intervalLengthMillis = current.to.getTime() - current.from.getTime();
        flowSummary.aggregateFlows(current.flows, intervalLengthMillis);
        flowSummary.aggregateThreads(current.threads, current.instrumentedTraceElements);
        if (logger.isLoggable(Level.FINE)) logger.fine(printFlowSummary(flowSummary));
        current.flowSummary = flowSummary;
        processedDataIndex.addFlowSummary(flowSummary);
    }

    private boolean isInstrumented(StackTraceElement traceElement) {
        if (step.get().currentInstrumentation == null) return false;
        if (step.get().instrumentedTraceElements.contains(traceElement)) return true;

        Set<MethodConfiguration> methods = step.get().currentInstrumentation.getMethods(NameUtils.getInternalClassName(traceElement.getClassName()));
        if (methods != null)
            for (MethodConfiguration method : methods) {
                if (method.methodName.equals(traceElement.getMethodName())) {
                    step.get().instrumentedTraceElements.add(traceElement);
                    return true;
                }
            }
        return false;
    }

    private String printFlowSummary(FlowSummary flowSummary) {
        String res = "\n-------- Flow summary ---------";
        for (MethodCall root : flowSummary.roots) {
            res += "\n" + DebugPrintUtil.methodCallSummaryStr("", root);
        }

        return res + "\n-----------------------------------\n";
    }

}
