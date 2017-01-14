package com.jflop.load;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.util.DigestUtil;
import org.elasticsearch.common.hash.MessageDigests;
import org.springframework.core.annotation.AnnotationAttributes;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

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

    public static GeneratedFlow generateFlow(int maxDepth, int maxLength, int maxDuration) {
        Set<String> availableMethods = new HashSet<>(Arrays.asList(allMethods));
        return new GeneratedFlow(generateFlowElement(availableMethods, maxDepth, maxLength, maxDuration));
    }

    public static Object[][] generateFlowsAndThroughput(int numFlows, int maxDepth, int maxLength, int maxDuration, int minThroughput, int maxThroughput) {
        Object[][] flowsThroughputs = new Object[numFlows][];
        for (int i = 0; i < numFlows; i++) {
            GeneratedFlow flow = GeneratedFlow.generateFlow(maxDepth, maxLength, maxDuration);
            float throughput = (float) random.nextInt(maxThroughput - minThroughput) + minThroughput;
            flowsThroughputs[i] = new Object[]{flow, throughput};
        }
        return flowsThroughputs;
    }

    private static FlowElement generateFlowElement(Set<String> availableMethods, int maxDepth, int maxLength, int maxDuration) {
        FlowElement element = new FlowElement();
        int pos = random.nextInt(availableMethods.size());

        String name = null;
        Iterator<String> iterator = availableMethods.iterator();
        for (int i = 0; i <= pos; i++) name = iterator.next();
        iterator.remove();

        try {
            element.mtd = getMethod(name);
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

    private static class FlowElement {

        private static final String NAME = "name";
        private static final String NESTED = "nested";
        private static final String DURATION = "duration";

        private Method mtd;
        private int processDuration;
        private List<FlowElement> nested;

        static FlowElement fromJson(Map json) throws NoSuchMethodException {
            String name = (String) json.get(NAME);
            FlowElement res = new FlowElement();
            res.mtd = getMethod(name);

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
    }
}
