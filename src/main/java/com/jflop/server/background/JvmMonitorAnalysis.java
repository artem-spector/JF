package com.jflop.server.background;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.feature.ClassInfoFeature;
import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.feature.SnapshotFeature;
import com.jflop.server.persistency.DocType;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.runtime.MetadataIndex;
import com.jflop.server.runtime.ProcessedDataIndex;
import com.jflop.server.runtime.RawDataIndex;
import com.jflop.server.runtime.data.*;
import com.jflop.server.runtime.data.metric.MetricData;
import com.jflop.server.runtime.data.metric.MetricMetadata;
import com.jflop.server.runtime.data.processed.FlowSummary;
import com.jflop.server.runtime.data.processed.MethodCall;
import com.jflop.server.runtime.data.processed.MethodFlow;
import com.jflop.server.util.DebugPrintUtil;
import org.jflop.config.JflopConfiguration;
import org.jflop.config.MethodConfiguration;
import org.jflop.config.NameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

    public static final String TASK_NAME = "JVMRawDataAnalysis";

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

    private File saveStepToFile;

    // step-level state
    public static class StepState {
        private AgentJVM agentJvm;
        AnalysisState taskState;
        Date from;
        Date to;
        public Map<ThreadMetadata, List<ThreadOccurrenceData>> threads;
        public Map<FlowMetadata, List<FlowOccurrenceData>> flows;
        public FlowSummary flowSummary;
        public MetricMetadata metricMetadata;
        public List<LoadData> loadData;
        Set<MethodConfiguration> methodsToInstrument;
        private Set<StackTraceElement> instrumentedTraceElements;
        AgentDataFactory agentDataFactory;

        void writeTo(File file) {
            Map<String, Object> stored = new HashMap<>();
            stored.put("threadMetadata", threads == null ? null : threads.keySet());
            stored.put("threadOccurrences", threads == null ? null : threads.values());
            stored.put("flowMetadata", flows == null ? null : flows.keySet());
            stored.put("flowOccurrences", flows == null ? null : flows.values());
            stored.put("loadData", loadData == null ? null : loadData);
            stored.put("metricMetadata", metricMetadata);
            try {
                FileOutputStream out = new FileOutputStream(file);
                new ObjectMapper().writeValue(out, stored);
                out.flush();
                out.close();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed saving step to file", e);
            }
        }

        public static StepState readFromFile(File file) throws IOException {
            ObjectMapper mapper = new ObjectMapper();
            Map map = mapper.readValue(file, Map.class);

            StepState res = new StepState();

            res.threads = new HashMap<>();
            List<ThreadMetadata> threadMetadata = get(mapper, map, "threadMetadata", new TypeReference<List<ThreadMetadata>>(){});
            List<List<ThreadOccurrenceData>> threadOccurrences = get(mapper, map, "threadOccurrences", new TypeReference<List<List<ThreadOccurrenceData>>>(){});
            threadMetadata.forEach(thread -> res.threads.put(thread, new ArrayList<>()));
            threadOccurrences.forEach(occ ->
            {
                for (Map.Entry<ThreadMetadata, List<ThreadOccurrenceData>> entry : res.threads.entrySet())
                    if (entry.getKey().getDocumentId().equals(occ.get(0).getMetadataId())) entry.getValue().addAll(occ);
            });

            res.flows = new HashMap<>();
            List<FlowMetadata> flowMetadata = get(mapper, map, "flowMetadata", new TypeReference<List<FlowMetadata>>(){});
            List<List<FlowOccurrenceData>> flowOccurrences = get(mapper, map, "flowOccurrences", new TypeReference<List<List<FlowOccurrenceData>>>(){});
            flowMetadata.forEach(flow -> res.flows.put(flow, new ArrayList<>()));
            flowOccurrences.forEach(occ ->
            {
                for (Map.Entry<FlowMetadata, List<FlowOccurrenceData>> entry : res.flows.entrySet())
                    if (entry.getKey().getDocumentId().equals(occ.get(0).getMetadataId())) entry.getValue().addAll(occ);
            });

            res.loadData = get(mapper, map, "loadData", new TypeReference<List<LoadData>>() {});

            res.metricMetadata = get(mapper, map, "metricMetadata", new TypeReference<MetricMetadata>() {});

            return res;
        }

        private static <T> T get(ObjectMapper mapper, Map map, String key, TypeReference<T> type) throws IOException {
            Object json = map.get(key);
            String jsonStr = mapper.writeValueAsString(json);
            return mapper.readValue(jsonStr, type);
        }
    }

    static ThreadLocal<StepState> step = new ThreadLocal<>();

    public JvmMonitorAnalysis() {
        super(TASK_NAME, 60, 3, 100);
    }

    @Override
    public void step(TaskLockData lock, Date refreshThreshold) {
        beforeStep(lock, refreshThreshold);
        analyze();
        takeSnapshot();
        afterStep(lock);
    }

    void beforeStep(TaskLockData lock, Date refreshThreshold) {
        StepState current = new StepState();
        current.agentJvm = lock.agentJvm;
        current.taskState = lock.getCustomState(AnalysisState.class);
        if (current.taskState == null) current.taskState = AnalysisState.createState();
        current.from = current.taskState.processedUntil;
        current.to = refreshThreshold;
        List<DocType> docTypes = new ArrayList<>(processedDataIndex.getDocTypes());
        docTypes.addAll(metadataIndex.getDocTypes());
        current.agentDataFactory = new AgentDataFactory(lock.agentJvm, new Date(), docTypes);

        JflopConfiguration lastReportedConfiguration = instrumentationConfigurationFeature.getConfiguration(current.agentJvm);
        if (lastReportedConfiguration != null)
            current.taskState.setInstrumentationConfig(lastReportedConfiguration);
        current.instrumentedTraceElements = new HashSet<>();
        current.methodsToInstrument = new HashSet<>();

        step.set(current);
    }

    void afterStep(TaskLockData lock) {
        StepState current = step.get();
        lock.setCustomState(current.taskState);
        step.remove();
        if (saveStepToFile != null) {
            current.writeTo(saveStepToFile);
            saveStepToFile = null;
        }
    }

    void analyze() {
        mapThreadsToFlows();
        buildMetrics();
        findMethodsToInstrumentInThreadDump();
        adjustInstrumentation();
    }

    void buildMetrics() {
        StepState current = step.get();
        PersistentData<MetricMetadata> metricMetadata = metadataIndex.getOrCreateMetricMetadata(current.agentDataFactory);
        current.metricMetadata = metricMetadata.source;

        Map<String, Float> observation = new HashMap<>();

        current.loadData = rawDataIndex.getLoadData(current.from, current.to);
        current.metricMetadata.aggregateLoad(current.loadData, observation);

        if (current.threads != null) {
            for (List<ThreadOccurrenceData> occurrenceList : current.threads.values()) {
                current.metricMetadata.aggregateThreads(occurrenceList, observation);
            }
        }

        if (current.flows != null) {
            for (List<FlowOccurrenceData> occurrenceList : current.flows.values()) {
                current.metricMetadata.aggregateFlows(occurrenceList, observation);
            }
        }

        metadataIndex.updateDocument(metricMetadata);
        MetricData metricData = current.agentDataFactory.createInstance(MetricData.class);
        metricData.metrics = observation;
        processedDataIndex.addMetrics(metricData);
    }

    void takeSnapshot() {
        StepState current = step.get();
        AnalysisState taskState = current.taskState;

        int duration = taskState.snapshotDuration;
        if (current.flowSummary != null && current.threads != null) {
            if (duration < 5 && needToIncreaseSnapshotDuration(duration)) {
                duration++;
                logger.fine("Snapshot duration increased to " + duration + " sec.");
            } else if (duration > 1 && needToDecreaseSnapshotDuration(duration)) {
                duration--;
                logger.fine("Snapshot duration decreased to " + duration + " sec.");
            }
        }

        boolean snapshotRequested = snapshotFeature.takeSnapshot(step.get().agentJvm, duration);
        if (snapshotRequested) {
            taskState.snapshotDuration = duration;
            taskState.processedUntil = current.to;
        }
    }

    // for tests only
    public void saveStepToFile(File file) {
        this.saveStepToFile = file;
    }

    private boolean needToDecreaseSnapshotDuration(float duration) {
        // if all flows happen more than 10 times in a snapshot, may decrease the duration
        return minFlowThroughput() * duration > 10;
    }

    private boolean needToIncreaseSnapshotDuration(float duration) {
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

    private void adjustInstrumentation() {
        JflopConfiguration oldConf = step.get().taskState.getInstrumentationConfig();
        JflopConfiguration newConf = new JflopConfiguration();
        if (oldConf != null)
            oldConf.getAllMethods().forEach(newConf::addMethodConfig);
        step.get().methodsToInstrument.forEach(newConf::addMethodConfig);

        Set<String> blacklist = metadataIndex.getBlacklistedClasses(step.get().agentJvm);
        for (String className : blacklist)
            newConf.removeClass(NameUtils.getInternalClassName(className));

        if (!newConf.equals(oldConf)) {
            instrumentationConfigurationFeature.setConfiguration(step.get().agentJvm, newConf);
            step.get().taskState.setInstrumentationConfig(newConf);
        }
    }

    void findMethodsToInstrumentInThreadDump() {
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
        boolean noThreads = current.threads == null || current.threads.isEmpty();
        if (logger.isLoggable(Level.FINE))
            logger.fine("Found " + (noThreads ? "no threads" : current.threads.size() + " distinct threads"));
        if (noThreads) return;

        // 2. get recent snapshots and their metadata
        current.flows = rawDataIndex.getOccurrencesAndMetadata(current.agentJvm, FlowOccurrenceData.class, FlowMetadata.class, current.from, current.to);
        boolean noFlows = current.flows == null || current.flows.isEmpty();
        if (logger.isLoggable(Level.FINE))
            logger.fine("Found " + (noFlows ? "no flows" : current.flows.size() + " distinct flows"));
        if (noFlows) return;

        // 3. build flow summary
        FlowSummary flowSummary = current.agentDataFactory.createInstance(FlowSummary.class);
        flowSummary.aggregateFlows(current.flows);
        flowSummary.aggregateThreads(current.threads);
        if (logger.isLoggable(Level.FINE)) logger.fine(DebugPrintUtil.printFlowSummary(flowSummary, logger.isLoggable(Level.FINEST)));
        current.flowSummary = flowSummary;
        processedDataIndex.addFlowSummary(flowSummary);
    }

    private boolean isInstrumented(StackTraceElement traceElement) {
        JflopConfiguration instrumentationConfig = step.get().taskState.getInstrumentationConfig();
        if (instrumentationConfig == null) return false;
        if (step.get().instrumentedTraceElements.contains(traceElement)) return true;

        Set<MethodConfiguration> methods = instrumentationConfig.getMethods(NameUtils.getInternalClassName(traceElement.getClassName()));
        if (methods != null)
            for (MethodConfiguration method : methods) {
                if (method.methodName.equals(traceElement.getMethodName())) {
                    step.get().instrumentedTraceElements.add(traceElement);
                    return true;
                }
            }
        return false;
    }

}
