package com.jflop.server.background;

import com.jflop.server.runtime.data.FlowMetadata;
import org.junit.Test;

import java.util.ArrayList;
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

    @Test
    public void testThreadToFlow() {

        MethodImpl m1 = new MethodImpl("MyClass", "m1", "MyClass.java", 100);
        MethodImpl m2 = new MethodImpl("MyClass", "m2", "MyClass.java", 200);
        MethodImpl m3 = new MethodImpl("MyClass", "m3", "MyClass.java", 300);
        MethodImpl m4 = new MethodImpl("MyClass", "m4", "MyClass.java", 400);

        // m1 -> m2
        //    -> m3
        MethodCall f1m1 = new MethodCall(m1);
        MethodCall f1m2 = f1m1.call(110, m2);
        f1m2.returnFrom(210);
        MethodCall f1m3 = f1m1.call(120, m3);
        f1m3.returnFrom(310);
        f1m1.returnFrom(199);
        FlowMetadata f1 = new FlowMetadata();
        f1.rootFlow = f1m1.getFlowElement();

        assertTrue(f1.fitsStacktrace(f1m1.getStackTrace()));
        assertTrue(f1.fitsStacktrace(f1m2.getStackTrace()));
        assertTrue(f1.fitsStacktrace(f1m3.getStackTrace()));

        // m1 -> m3
        MethodCall f2m1 = new MethodCall(m1);
        MethodCall f2m3 = f2m1.call(120, m3);
        f2m3.returnFrom(310);
        f2m1.returnFrom(199);
        FlowMetadata f2 = new FlowMetadata();
        f2.rootFlow = f2m1.getFlowElement();

        assertTrue(f2.fitsStacktrace(f2m1.getStackTrace()));
        assertTrue(f2.fitsStacktrace(f2m3.getStackTrace()));

        assertTrue(f1.fitsStacktrace(f2m1.getStackTrace()));
        assertTrue(f2.fitsStacktrace(f1m1.getStackTrace()));
        assertTrue(f1.fitsStacktrace(f2m3.getStackTrace()));
        assertTrue(f2.fitsStacktrace(f1m3.getStackTrace()));

        assertFalse(f2.fitsStacktrace(f1m2.getStackTrace()));
    }


    private static class MethodImpl {

        String className;
        String method;
        String file;
        int firstLine;

        public MethodImpl(String className, String method, String file, int firstLine) {
            this.className = className;
            this.method = method;
            this.file = file;
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
                int line = calledFromLine;
                curr = curr.caller;
                res.add(new StackTraceElement(curr.impl.className, curr.impl.method, curr.impl.file, line));
            }

            return res.toArray(new StackTraceElement[res.size()]);
        }

        public FlowMetadata.FlowElement getFlowElement() {
            FlowMetadata.FlowElement root = new FlowMetadata.FlowElement();
            root.className = impl.className;
            root.methodName = impl.method;
            root.fileName = impl.file;
            root.firstLine = String.valueOf(impl.firstLine);
            root.returnLine = String.valueOf(returnLine);

            root.subflows = nestedCalls.stream().map(MethodCall::getFlowElement).collect(Collectors.toList());
            return root;
        }

        public FlowMetadata asFlow() {
            FlowMetadata res = new FlowMetadata();
            res.rootFlow = getFlowElement();
            return res;
        }
    }
}
