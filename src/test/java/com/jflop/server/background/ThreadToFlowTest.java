package com.jflop.server.background;

import com.jflop.server.runtime.data.FlowMetadata;
import org.jflop.config.NameUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

    private MethodCall f1m1;
    private MethodCall f1m2;
    private MethodCall f1m3;
    private MethodCall f1m4;
    private MethodCall f2m1;
    private MethodCall f2m3;
    private MethodCall f2m4;

    @Before
    public void createFlows() {
        // m1 -> m2
        //    -> m3 -> m4
        f1m1 = new MethodCall(m1);
        f1m2 = f1m1.call(110, m2);
        f1m2.returnFrom(210);
        f1m3 = f1m1.call(120, m3);
        f1m4 = f1m3.call(310, m4);
        f1m4.returnFrom(410);
        f1m3.returnFrom(320);
        f1m1.returnFrom(199);

        // m1 -> m3 -> m4
        f2m1 = new MethodCall(m1);
        f2m3 = f2m1.call(120, m3);
        f2m4 = f2m3.call(310, m4);
        f2m4.returnFrom(410);
        f2m3.returnFrom(320);
        f2m1.returnFrom(199);
    }

    @Test
    public void testAllInstrumented() {
        List<FlowMetadata> flows = getFlows(f1m1);
        assertTrue(flows != null && flows.size() == 1);
        FlowMetadata f1 = flows.get(0);

        flows = getFlows(f2m1);
        assertTrue(flows != null && flows.size() == 1);
        FlowMetadata f2 = flows.get(0);

        assertTrue(f1.fitsStacktrace(f1m1.getStackTrace()));
        assertTrue(f1.fitsStacktrace(f1m2.getStackTrace()));
        assertTrue(f1.fitsStacktrace(f1m3.getStackTrace()));
        assertTrue(f1.fitsStacktrace(f1m4.getStackTrace()));

        assertTrue(f2.fitsStacktrace(f2m1.getStackTrace()));
        assertTrue(f2.fitsStacktrace(f2m3.getStackTrace()));
        assertTrue(f2.fitsStacktrace(f2m4.getStackTrace()));

        assertTrue(f1.fitsStacktrace(f2m1.getStackTrace()));
        assertTrue(f2.fitsStacktrace(f1m1.getStackTrace()));
        assertTrue(f1.fitsStacktrace(f2m3.getStackTrace()));
        assertTrue(f2.fitsStacktrace(f1m3.getStackTrace()));
        assertTrue(f1.fitsStacktrace(f2m4.getStackTrace()));
        assertTrue(f2.fitsStacktrace(f1m4.getStackTrace()));

        assertFalse(f2.fitsStacktrace(f1m2.getStackTrace()));
    }

    @Test
    public void testSometNotInstrumented() {
        // first method not instrumented
        m1.instrumented = false;

        List<FlowMetadata> flows = getFlows(f1m1);
        assertTrue(flows != null && flows.size() == 2);
        FlowMetadata f11 = flows.get(0);
        FlowMetadata f12 = flows.get(1);

        assertFalse(f11.fitsStacktrace(f1m1.getStackTrace()));
        assertFalse(f12.fitsStacktrace(f1m1.getStackTrace()));

        assertTrue(f11.fitsStacktrace(f1m2.getStackTrace()));
        assertFalse(f12.fitsStacktrace(f1m2.getStackTrace()));

        assertFalse(f11.fitsStacktrace(f1m3.getStackTrace()));
        assertTrue(f12.fitsStacktrace(f1m3.getStackTrace()));

        assertFalse(f11.fitsStacktrace(f1m4.getStackTrace()));
        assertTrue(f12.fitsStacktrace(f1m4.getStackTrace()));

        // last method not instrumented
        m1.instrumented = true;
        m4.instrumented = false;
        flows = getFlows(f1m1);
        assertTrue(flows != null && flows.size() == 1);
        FlowMetadata f1 = flows.get(0);

        assertTrue(f1.fitsStacktrace(f1m1.getStackTrace()));
        assertTrue(f1.fitsStacktrace(f1m2.getStackTrace()));
        assertTrue(f1.fitsStacktrace(f1m3.getStackTrace()));
        assertTrue(f1.fitsStacktrace(f1m4.getStackTrace()));
    }

    private List<FlowMetadata> getFlows(MethodCall mc) {
        return mc.getFlowElements().stream().map(flowElement -> {
            FlowMetadata metadata = new FlowMetadata();
            metadata.rootFlow = flowElement;
            return metadata;
        }).collect(Collectors.toList());
    }

    private static class MethodImpl {

        String className;
        String method;
        String file;
        int firstLine;
        boolean instrumented = true;

        public MethodImpl(String className, String method, String file, int firstLine) {
            this.className = className;
            this.method = method;
            this.file = file;
            this.firstLine = firstLine;
        }
    }

    private static class MethodCall {

        MethodImpl impl;
        int returnLine;

        MethodCall caller;
        int calledFromLine;

        List<MethodCall> nestedCalls = new ArrayList<>();

        public MethodCall(MethodImpl impl) {
            this.impl = impl;
        }

        public MethodCall returnFrom(int line) {
            returnLine = line;
            return caller;
        }

        public MethodCall call(int line, MethodImpl impl) {
            MethodCall nested = new MethodCall(impl);
            nestedCalls.add(nested);
            nested.caller = this;
            nested.calledFromLine = line;
            return nested;
        }

        public StackTraceElement[] getStackTrace() {
            List<StackTraceElement> res = new ArrayList<>();
            res.add(new StackTraceElement(impl.className, impl.method, impl.file, impl.firstLine + 1));

            MethodCall curr = this;
            while (curr.caller != null) {
                int line = curr.calledFromLine;
                curr = curr.caller;
                res.add(new StackTraceElement(curr.impl.className, curr.impl.method, curr.impl.file, line));
            }

            return res.toArray(new StackTraceElement[res.size()]);
        }

        public List<FlowMetadata.FlowElement> getFlowElements() {
            List<FlowMetadata.FlowElement> subflows = null;
            if (nestedCalls != null && !nestedCalls.isEmpty()) {
                subflows = new ArrayList<>();
                for (MethodCall nestedCall : nestedCalls) {
                    List<FlowMetadata.FlowElement> flowElements = nestedCall.getFlowElements();
                    if (flowElements != null)
                        subflows.addAll(flowElements);
                }
            }

            if (!impl.instrumented) return subflows;

            FlowMetadata.FlowElement root = new FlowMetadata.FlowElement();
            root.className = NameUtils.getInternalClassName(impl.className);
            root.methodName = impl.method;
            root.fileName = impl.file;
            root.firstLine = String.valueOf(impl.firstLine);
            root.returnLine = String.valueOf(returnLine);
            root.subflows = subflows;

            return Collections.singletonList(root);
        }
    }
}
