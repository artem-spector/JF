package com.jflop.server.background;

import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.feature.ClassInfoFeature;
import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.feature.SnapshotFeature;
import com.jflop.server.runtime.MetadataIndex;
import com.jflop.server.runtime.RawDataIndex;
import com.jflop.server.runtime.data.*;
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
        Map<ThreadMetadata, List<FlowMetadata>> threadsToFlows;
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
        step.get().agentDataFactory = new AgentDataFactory(lock.agentJvm, refreshThreshold, rawDataIndex.getDocTypes());

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
        logger.fine("Taking snapshot");
        snapshotFeature.takeSnapshot(step.get().agentJvm, 1);
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
        Map<String, List<String>> missingSignatures = new HashMap<>();

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
                        List<String> methods = missingSignatures.computeIfAbsent(internalClassName, k -> new ArrayList<>());
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
        current.threadsToFlows = new HashMap<>();
        current.flows = rawDataIndex.getOccurrencesAndMetadata(current.agentJvm, FlowOccurrenceData.class, FlowMetadata.class, current.from, current.to);
        if (current.flows == null || current.flows.isEmpty()) return;
        if (logger.isLoggable(Level.FINE)) logger.fine("Found " + current.flows.size() + " distinct flows");

        // 3. loop by threads and find corresponding flows
        for (ThreadMetadata thread : current.threads.keySet()) {
            // make sure for each trace element we know whether it's instrumented
            for (StackTraceElement traceElement : thread.stackTrace) isInstrumented(traceElement);

            // find out which flows fit the current trace
            for (FlowMetadata flow : current.flows.keySet()) {
                if (flow.fitsStacktrace(thread.stackTrace, current.instrumentedTraceElements)) {
                    current.threadsToFlows.computeIfAbsent(thread, threadMetadata -> new ArrayList<>()).add(flow);
                }
            }
        }

        if (logger.isLoggable(Level.FINE)) logger.fine(printThreadToFlows());

        // build traced flows
        Map<TracedFlowMetadata, List<TracedFlowOccurrence>> tracedFlows = new HashMap<>();
        for (Map.Entry<ThreadMetadata, List<ThreadOccurrenceData>> threadEntry : current.threads.entrySet()) {
            ThreadMetadata threadMetadata = threadEntry.getKey();
            List<ThreadOccurrenceData> threadOccurrences = threadEntry.getValue();
            List<FlowOccurrenceData> flowOccurrences = new ArrayList<>();
            TracedFlowMetadata tracedFlowMetadata = null;

            for (Map.Entry<FlowMetadata, List<FlowOccurrenceData>> flowsEntry : current.flows.entrySet()) {
                FlowMetadata flowMetadata = flowsEntry.getKey();
                TracedFlowMetadata tracedFlow = TracedFlowMetadata.getTracedFlow(flowMetadata, threadMetadata, current.instrumentedTraceElements);
                if (tracedFlow != null) {
                    flowOccurrences.addAll(flowsEntry.getValue());
                    if (tracedFlowMetadata == null) tracedFlowMetadata = tracedFlow;
                    assert tracedFlow.equals(tracedFlowMetadata);
                }
            }

            if (tracedFlowMetadata != null) {
                tracedFlows.computeIfAbsent(tracedFlowMetadata, key -> new ArrayList<>())
                        .add(new TracedFlowOccurrence(tracedFlowMetadata, flowOccurrences, threadOccurrences));
            }
        }
        if (logger.isLoggable(Level.FINE)) logger.fine(printTracedFlows(tracedFlows));

        // build aggregated flows
        Map<FlowMetadata, AggregatedFlowOccurrence> aggregatedFlows = AggregatedFlowOccurrence.aggregate(current.flows, current.threads,
                current.agentDataFactory, current.instrumentedTraceElements, current.to.getTime() - current.from.getTime());
        if (logger.isLoggable(Level.FINE)) logger.fine(printAggregatedFlows(aggregatedFlows));

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

    private String printThreadToFlows() {
        String res = "\n-------- threads to flows ---------";
        for (Map.Entry<ThreadMetadata, List<FlowMetadata>> entry : step.get().threadsToFlows.entrySet()) {
            ThreadMetadata threadMetadata = entry.getKey();
            res += "\n\n" + DebugPrintUtil.threadMetadataAndOccurrencesStr(threadMetadata, step.get().threads.get(threadMetadata));
            for (FlowMetadata metadata : entry.getValue()) {
                res += "\n\t" + DebugPrintUtil.flowMetadataAndOccurrencesStr(metadata, step.get().flows.get(metadata));
            }
        }
        return res + "\n-----------------------------------\n";
    }

    private String printTracedFlows(Map<TracedFlowMetadata, List<TracedFlowOccurrence>> tracedFlows) {
        String res = "\n-------- traced flows ---------";
        for (Map.Entry<TracedFlowMetadata, List<TracedFlowOccurrence>> entry : tracedFlows.entrySet()) {
            res += DebugPrintUtil.tracedFlowMetadataAndOccurrencesStr(entry.getKey(), entry.getValue());
        }
        return res + "\n-----------------------------------\n";
    }

    private String printAggregatedFlows(Map<FlowMetadata, AggregatedFlowOccurrence> aggregatedFlows) {
        String res = "\n-------- aggregated flows ---------";
        for (Map.Entry<FlowMetadata, AggregatedFlowOccurrence> entry : aggregatedFlows.entrySet()) {
            res += DebugPrintUtil.aggregatedFlowMetadataAndOccurrenceStr(entry.getKey(), entry.getValue());
        }
        return res + "\n-----------------------------------\n";
    }

}
