package com.jflop.server.background;

import com.jflop.server.persistency.ValuePair;
import com.jflop.server.runtime.data.FlowMetadata;
import com.jflop.server.runtime.data.FlowOccurrenceData;
import com.jflop.server.runtime.data.processed.FlowSummary;
import com.jflop.server.runtime.data.processed.MethodCall;
import com.jflop.server.util.DigestUtil;
import org.elasticsearch.common.hash.MessageDigests;
import org.jflop.config.NameUtils;
import org.junit.Before;
import org.junit.Test;

import java.security.MessageDigest;
import java.util.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TODO: Document!
 *
 * @author artem on 25/12/2016.
 */
public class ThreadToFlowTest {

    private MethodImpl m1 = new MethodImpl("com.sample.MyClass", "m1", "MyClass.java", 100);
    private MethodImpl m2 = new MethodImpl("com.sample.MyClass", "m2", "MyClass.java", 200);
    private MethodImpl m3 = new MethodImpl("com.sample.MyClass", "m3", "MyClass.java", 300);
    private MethodImpl m4 = new MethodImpl("com.sample.MyClass", "m4", "MyClass.java", 400);

    private Invocation f1m1;
    private Invocation f1m2;
    private Invocation f1m3;
    private Invocation f1m4;
    private Invocation f2m1;
    private Invocation f2m3;
    private Invocation f2m4;

    @Before
    public void createFlows() {
        // m1 -> m2
        //    -> m3 -> m4
        f1m1 = new Invocation(m1);
        f1m2 = f1m1.call(110, m2);
        f1m2.returnFrom(210);
        f1m3 = f1m1.call(120, m3);
        f1m4 = f1m3.call(310, m4);
        f1m4.returnFrom(410);
        f1m3.returnFrom(320);
        f1m1.returnFrom(199);

        // m1 -> m3 -> m4
        f2m1 = new Invocation(m1);
        f2m3 = f2m1.call(120, m3);
        f2m4 = f2m3.call(310, m4);
        f2m4.returnFrom(410);
        f2m3.returnFrom(320);
        f2m1.returnFrom(199);
    }

    @Test
    public void testAllInstrumented() {
        List<MethodCall> methodCalls = f1m1.getMethodCalls();
        assertTrue(methodCalls != null && methodCalls.size() == 1);
        MethodCall call1 = methodCalls.get(0);

        methodCalls = f2m1.getMethodCalls();
        assertTrue(methodCalls != null && methodCalls.size() == 1);
        MethodCall call2 = methodCalls.get(0);

        assertTrue(fit(call1, f1m1.getStackTrace()));
        assertTrue(fit(call1, f1m2.getStackTrace()));
        assertTrue(fit(call1, f1m3.getStackTrace()));
        assertTrue(fit(call1, f1m4.getStackTrace()));

        assertTrue(fit(call2, f2m1.getStackTrace()));
        assertTrue(fit(call2, f2m3.getStackTrace()));
        assertTrue(fit(call2, f2m4.getStackTrace()));

        assertTrue(fit(call1, f2m1.getStackTrace()));
        assertTrue(fit(call2, f1m1.getStackTrace()));
        assertTrue(fit(call1, f2m3.getStackTrace()));
        assertTrue(fit(call2, f1m3.getStackTrace()));
        assertTrue(fit(call1, f2m4.getStackTrace()));
        assertTrue(fit(call2, f1m4.getStackTrace()));

        assertFalse(fit(call2, f1m2.getStackTrace()));
    }

    @Test
    public void testSometNotInstrumented() {
        // first method not instrumented
        m1.instrumented = false;

        List<MethodCall> methodCalls = f1m1.getMethodCalls();
        assertTrue(methodCalls != null && methodCalls.size() == 2);
        MethodCall f11 = methodCalls.get(0);
        MethodCall f12 = methodCalls.get(1);

        assertFalse(fit(f11, f1m1.getStackTrace()));
        assertFalse(fit(f12, f1m1.getStackTrace()));

        assertTrue(fit(f11, f1m2.getStackTrace()));
        assertFalse(fit(f12, f1m2.getStackTrace()));

        assertFalse(fit(f11, f1m3.getStackTrace()));
        assertTrue(fit(f12, f1m3.getStackTrace()));

        assertFalse(fit(f11, f1m4.getStackTrace()));
        assertTrue(fit(f12, f1m4.getStackTrace()));

        // last method not instrumented
        m1.instrumented = true;
        m4.instrumented = false;
        methodCalls = f1m1.getMethodCalls();
        assertTrue(methodCalls != null && methodCalls.size() == 1);
        MethodCall f1 = methodCalls.get(0);

        assertTrue(fit(f1, f1m1.getStackTrace()));
        assertTrue(fit(f1, f1m2.getStackTrace()));
        assertTrue(fit(f1, f1m3.getStackTrace()));
        assertTrue(fit(f1, f1m4.getStackTrace()));
    }

    @Test
    public void testShortFlow() {
        // f1m1:
        // m1 -> m2
        //    -> m3 -> m4

        // f2m1:
        // m1 -> m2 -> m4
        f2m1 = new Invocation(m1);
        Invocation f2m2 = f2m1.call(120, m2);
        f2m4 = f2m2.call(210, m4);
        f2m4.returnFrom(410);
        f2m2.returnFrom(220);
        f2m1.returnFrom(199);

        MethodCall f1 = f1m1.getMethodCalls().get(0);
        assertFalse(fit(f1, f2m4.getStackTrace()));
    }

    private boolean fit(MethodCall call, StackTraceElement[] stackTrace) {
        List<ValuePair<MethodCall, Integer>> path = new ArrayList<>();
        return FlowSummary.findPath(call, stackTrace, stackTrace.length - 1, getInstrumentedMethods(), path);
    }

    private Set<StackTraceElement> getInstrumentedMethods() {
        Set<StackTraceElement> res = new HashSet<>();
        MethodImpl[] allmethods = {m1, m2, m3, m4};
        for (MethodImpl method : allmethods) {
            if (method.instrumented) res.add(method.getStackTraceElement());
        }
        return res;
    }

    private static class MethodImpl {

        String className;
        String method;
        String file;
        int firstLine;
        boolean instrumented = true;

        MethodImpl(String className, String method, String file, int firstLine) {
            this.className = className;
            this.method = method;
            this.file = file;
            this.firstLine = firstLine;
        }

        StackTraceElement getStackTraceElement() {
            return new StackTraceElement(className, method, file, firstLine + 1);
        }
    }

    private static class Invocation {

        MethodImpl impl;
        int returnLine;

        Invocation caller;
        int calledFromLine;

        List<Invocation> nestedCalls = new ArrayList<>();

        Invocation(MethodImpl impl) {
            this.impl = impl;
        }

        Invocation returnFrom(int line) {
            returnLine = line;
            return caller;
        }

        Invocation call(int line, MethodImpl impl) {
            Invocation nested = new Invocation(impl);
            nestedCalls.add(nested);
            nested.caller = this;
            nested.calledFromLine = line;
            return nested;
        }

        StackTraceElement[] getStackTrace() {
            List<StackTraceElement> res = new ArrayList<>();
            res.add(impl.getStackTraceElement());

            Invocation curr = this;
            while (curr.caller != null) {
                int line = curr.calledFromLine;
                curr = curr.caller;
                res.add(new StackTraceElement(curr.impl.className, curr.impl.method, curr.impl.file, line));
            }

            return res.toArray(new StackTraceElement[res.size()]);
        }

        ValuePair<List<FlowMetadata.FlowElement>, List<FlowOccurrenceData.FlowElement>> getFlowElements() {
            List<FlowMetadata.FlowElement> subMeta = null;
            List<FlowOccurrenceData.FlowElement> subOccurrences = null;
            if (nestedCalls != null && !nestedCalls.isEmpty()) {
                subMeta = new ArrayList<>();
                subOccurrences = new ArrayList<>();
                for (Invocation nestedCall : nestedCalls) {
                    ValuePair<List<FlowMetadata.FlowElement>, List<FlowOccurrenceData.FlowElement>> flowElements = nestedCall.getFlowElements();
                    if (flowElements.value1 != null && flowElements.value2 != null) {
                        subMeta.addAll(flowElements.value1);
                        subOccurrences.addAll(flowElements.value2);
                    }
                }
            }

            if (!impl.instrumented) return new ValuePair<>(subMeta, subOccurrences);

            FlowMetadata.FlowElement metadataElement = new FlowMetadata.FlowElement();
            metadataElement.className = NameUtils.getInternalClassName(impl.className);
            metadataElement.methodName = impl.method;
            metadataElement.fileName = impl.file;
            metadataElement.firstLine = String.valueOf(impl.firstLine);
            metadataElement.returnLine = String.valueOf(returnLine);
            metadataElement.subflows = subMeta;

            MessageDigest digest = MessageDigests.sha1();
            DigestUtil.addStringsToDigest(digest, metadataElement.className, metadataElement.methodName);
            if (metadataElement.subflows != null)
                for (FlowMetadata.FlowElement subflow : metadataElement.subflows)
                    DigestUtil.addStringsToDigest(digest, subflow.flowId);
            metadataElement.flowId = DigestUtil.digestToHexString(digest);

            FlowOccurrenceData.FlowElement occurrenceElement = new FlowOccurrenceData.FlowElement();
            occurrenceElement.flowId = metadataElement.flowId;
            occurrenceElement.count = 1;
            occurrenceElement.cumulativeTime = 10;
            occurrenceElement.maxTime = 10;
            occurrenceElement.minTime = 10;
            occurrenceElement.subflows = subOccurrences;

            return new ValuePair<>(Collections.singletonList(metadataElement), Collections.singletonList(occurrenceElement));
        }

        List<MethodCall> getMethodCalls() {
            List<MethodCall> calls = new ArrayList<>();

            ValuePair<List<FlowMetadata.FlowElement>, List<FlowOccurrenceData.FlowElement>> flowRoots = getFlowElements();
            List<FlowMetadata.FlowElement> meta = flowRoots.value1;
            List<FlowOccurrenceData.FlowElement> occ = flowRoots.value2;

            for (int i = 0; i < meta.size(); i++) {
                FlowMetadata flowMetadata = new FlowMetadata();
                flowMetadata.rootFlow = meta.get(i);

                FlowOccurrenceData flowOccurrence = new FlowOccurrenceData();
                flowOccurrence.rootFlow = occ.get(i);

                MethodCall methodCall = new MethodCall(flowMetadata.rootFlow);
                methodCall.addFlow(flowMetadata, Collections.singletonList(flowOccurrence), 10);
                calls.add(methodCall);
            }

            return calls;
        }

    }
}
