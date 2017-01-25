package com.jflop.load;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.runtime.data.FlowMetadata;
import com.jflop.server.runtime.data.processed.FlowSummary;
import com.jflop.server.runtime.data.processed.MethodCall;
import com.jflop.server.runtime.data.processed.MethodFlow;
import com.jflop.server.util.DigestUtil;
import org.elasticsearch.common.hash.MessageDigests;
import org.jflop.config.JflopConfiguration;
import org.jflop.config.MethodConfiguration;
import org.springframework.core.annotation.AnnotationAttributes;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

/**
 * TODO: Document!
 *
 * @author artem on 10/01/2017.
 */
public class GeneratedFlow implements FlowMockup {

    private static ObjectMapper mapper = new ObjectMapper();
    private static Random random = new Random();

    private static String[] allMethods = {"m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8"};

    private FlowElement root;
    private String id;
    private static ThreadLocal<Stack<FlowElement>> current = new ThreadLocal<>();

    public void m1() {
        generatedMethodImpl();
    }

    public void m2() {
        generatedMethodImpl();
    }

    public void m3() {
        generatedMethodImpl();
    }

    public void m4() {
        generatedMethodImpl();
    }

    public void m5() {
        generatedMethodImpl();
    }

    public void m6() {
        generatedMethodImpl();
    }

    public void m7() {
        generatedMethodImpl();
    }

    public void m8() {
        generatedMethodImpl();
    }

    private void generatedMethodImpl() {
        FlowElement element = current.get().peek();
        element.process();
        if (element.nested != null) {
            for (FlowElement subFlow : element.nested) {
                current.get().push(subFlow);
                callCurrent();
            }
        }
    }

    public static GeneratedFlow generateFlow(int maxDepth, int maxLength, int minDuration, int maxDuration) {
        GeneratedFlow flow;
        do {
            Set<String> availableMethods = new HashSet<>(Arrays.asList(allMethods));
            flow = new GeneratedFlow(generateFlowElement(availableMethods, maxDepth, maxLength, maxDuration));
        } while (flow.getExpectedDurationMillis() < minDuration);
        return flow;
    }

    public static Object[][] generateFlowsAndThroughput(int numFlows, int maxDepth, int maxLength, int minDuration, int maxDuration, int minThroughput, int maxThroughput) {
        Object[][] flowsThroughputs = new Object[numFlows][];
        for (int i = 0; i < numFlows; i++) {
            GeneratedFlow flow = GeneratedFlow.generateFlow(maxDepth, maxLength, minDuration, maxDuration);
            float throughput = (float) random.nextInt(maxThroughput - minThroughput + 1) + minThroughput;
            flowsThroughputs[i] = new Object[]{flow, throughput};
        }
        return flowsThroughputs;
    }

    private static FlowElement generateFlowElement(Set<String> availableMethods, int maxDepth, int maxLength, int maxDuration) {
        FlowElement element = new FlowElement();
        assertTrue(availableMethods.size() >= 1);
        int pos = random.nextInt(availableMethods.size());

        String name = null;
        Iterator<String> iterator = availableMethods.iterator();
        for (int i = 0; i <= pos; i++) name = iterator.next();
        iterator.remove();

        try {
            element.setMtd(getMethod(name));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to generate flow element for method '" + name + "'", e);
        }

        if (maxDuration > 0) {
            element.processDuration = random.nextInt(maxDuration + 1);
            maxDuration -= element.processDuration;
        }

        if (maxDepth > 0) {
            int length = random.nextInt(maxLength + 1);
            if (length > 0) {
                element.nested = new ArrayList<>(length);
                for (int i = 0; i < length; i++)
                    if (!availableMethods.isEmpty())
                        element.nested.add(generateFlowElement(availableMethods, maxDepth - 1, maxLength, maxDuration));
            }
        }

        return element;
    }

    public static void save(File file, Object[][] flowsAndThroughput) throws IOException {
        List<List<Object>> json = new ArrayList<>();
        for (Object[] pair : flowsAndThroughput) {
            json.add(Arrays.asList(pair[0].toString(), pair[1]));
        }

        mapper.writeValue(file, json);
    }

    public static Object[][] read(File file) throws IOException {
        TypeReference type = new TypeReference<List<List<Object>>>() {
        };
        List<List<Object>> json = mapper.readValue(file, type);

        Object[][] res = new Object[json.size()][2];
        for (int i = 0; i < res.length; i++) {
            List<Object> pair = json.get(i);
            res[i][0] = fromString((String) pair.get(0));
            res[i][1] = ((Double)pair.get(1)).floatValue();
        }

        return res;
    }

    public static GeneratedFlow fromString(String str) {
        try {
            Map json = mapper.readValue(str, Map.class);
            return new GeneratedFlow(FlowElement.fromJson(json));
        } catch (Exception e) {
            throw new RuntimeException("Failed to read flow from string " + str, e);
        }
    }

    private static Method getMethod(String name) throws NoSuchMethodException {
        return GeneratedFlow.class.getMethod(name);
    }

    public GeneratedFlow(FlowElement root) {
        this.root = root;
        MessageDigest digest = MessageDigests.sha1();
        DigestUtil.addStringsToDigest(digest, root.concatMethodNames());
        id = DigestUtil.digestToHexString(digest);
    }

    @Override
    public String toString() {
        try {
            return mapper.writeValueAsString(root.toJson());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to save flow to string", e);
        }
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || !(obj == null || !(obj instanceof GeneratedFlow)) && id.equals(((GeneratedFlow) obj).id);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public long getExpectedDurationMillis() {
        return root.calculateDuration();
    }

    @Override
    public void go() {
        current.set(new Stack<>());
        current.get().push(root);
        callCurrent();
    }

    private void callCurrent() {
        FlowElement element = current.get().peek();
        element.call(this);
        current.get().pop();
    }

    public Set<String> findFlowIds(FlowSummary summary, Map<String, FlowMetadata> flows) {
        Set<String> res = new HashSet<>();
        FlowElement expectedRoot = this.root;

        for (MethodCall recordedRoot : summary.roots) {
            for (MethodFlow methodFlow : recordedRoot.flows) {
                String flowId = methodFlow.flowId;
                JflopConfiguration jflopConfiguration = JflopConfiguration.fromJson(flows.get(flowId).instrumentedMethodsJson);
                if (flowFits(expectedRoot, recordedRoot, flowId, jflopConfiguration)) {
                    res.add(flowId);
                }
            }
        }

        return res;
    }

    private static boolean flowFits(FlowElement expectedFlow, MethodCall recordedCall, String flowId, JflopConfiguration instrumentation) {

        MethodConfiguration expectedMtd = expectedFlow.methodConfiguration;
        boolean expectedIsInstrumented = instrumentation.containsMethod(expectedMtd);

        if (recordedCall == null)
            return !expectedIsInstrumented && !expectedFlow.hasInstrumentedSubelements(instrumentation);

        if (recordedCall.flows.stream().noneMatch(methodFlow -> methodFlow.flowId.equals(flowId)))
            return false;

        MethodConfiguration recordedMtd = new MethodConfiguration(recordedCall.className, recordedCall.methodName, recordedCall.methodDescriptor);

        if (expectedIsInstrumented && expectedMtd.equals(recordedMtd))
            return !expectedFlow.hasInstrumentedSubelements(instrumentation) || nestedFit(expectedFlow, recordedCall, flowId, instrumentation);

        if (expectedIsInstrumented && expectedFlow.containsMethod(recordedMtd))
            return false;

        if (expectedIsInstrumented)
            return recordedCall.nestedCalls != null && recordedCall.nestedCalls.stream().anyMatch(recordedNested -> flowFits(expectedFlow, recordedNested, flowId, instrumentation));

        return expectedFlow.nested == null || expectedFlow.nested.isEmpty()
                || expectedFlow.nested.stream().anyMatch(expectedNested -> flowFits(expectedNested, recordedCall, flowId, instrumentation));
    }

    private static boolean nestedFit(FlowElement expectedFlow, MethodCall recordedCall, String flowId, JflopConfiguration instrumentation) {
        boolean expectedHasNested = expectedFlow.nested != null && !expectedFlow.nested.isEmpty();
        boolean recordedHasNested = recordedCall != null && recordedCall.nestedCalls != null && !recordedCall.nestedCalls.isEmpty();

        if (!expectedHasNested)
            return true;

        for (FlowElement expectedNested : expectedFlow.nested) {
            if (recordedHasNested) {
                if (recordedCall.nestedCalls.stream().noneMatch(recordedNested -> flowFits(expectedNested, recordedNested, flowId, instrumentation)))
                    return false;
            } else {
                if (!flowFits(expectedNested, null, flowId, instrumentation))
                    return false;
            }
        }
        return true;
    }

    private static class FlowElement {

        private static final String NAME = "name";
        private static final String NESTED = "nested";
        private static final String DURATION = "duration";

        private Method mtd;
        private MethodConfiguration methodConfiguration;
        private int processDuration;
        private List<FlowElement> nested;

        static FlowElement fromJson(Map json) throws NoSuchMethodException {
            String name = (String) json.get(NAME);
            FlowElement res = new FlowElement();
            res.setMtd(getMethod(name));

            res.processDuration = (int) json.get(DURATION);

            List nestedJson = (List) json.get(NESTED);
            if (nestedJson != null) {
                res.nested = new ArrayList<>();
                for (Object obj : nestedJson) {
                    res.nested.add(fromJson((Map) obj));
                }
            }

            return res;
        }

        public void setMtd(Method mtd) {
            this.mtd = mtd;
            methodConfiguration = new MethodConfiguration(mtd);
        }

        boolean isInstrumented(JflopConfiguration configuration) {
            return configuration.containsMethod(methodConfiguration);
        }

        boolean hasInstrumentedSubelements(JflopConfiguration configuration) {
            if (nested == null || nested.isEmpty()) return false;

            for (FlowElement subelement : nested) {
                if (subelement.isInstrumented(configuration) || subelement.hasInstrumentedSubelements(configuration))
                    return true;
            }

            return false;
        }

        Object toJson() {
            Map<String, Object> res = new AnnotationAttributes();
            res.put(NAME, mtd.getName());
            res.put(DURATION, processDuration);
            if (nested != null) {
                ArrayList<Object> nestedJson = new ArrayList<>();
                this.nested.forEach(element -> nestedJson.add(element.toJson()));
                res.put(NESTED, nestedJson);
            }
            return res;
        }

        void call(GeneratedFlow flow) {
            try {
                mtd.invoke(flow);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        void process() {
            if (processDuration == 0) return;
            try {
                Thread.sleep(processDuration);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        String concatMethodNames() {
            String res = mtd.getName();
            if (nested != null)
                res += nested.stream().map(FlowElement::concatMethodNames).collect(Collectors.joining(",", "->", ""));
            return res;
        }

        long calculateDuration() {
            long res = processDuration;
            if (nested != null)
                res += nested.stream().mapToLong(FlowElement::calculateDuration).sum();
            return res;
        }

        public boolean containsMethod(MethodConfiguration methodConfiguration) {
            return this.methodConfiguration.equals(methodConfiguration)
                    || nested != null && nested.stream().anyMatch(flowElement -> flowElement.containsMethod(methodConfiguration));
        }
    }
}
