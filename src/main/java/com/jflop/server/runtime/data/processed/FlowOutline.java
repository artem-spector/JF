package com.jflop.server.runtime.data.processed;

import com.jflop.server.runtime.data.ThreadMetadata;
import org.jflop.config.NameUtils;

import java.util.*;

/**
 * TODO: Document!
 *
 * @author artem on 23/01/2017.
 */
public class FlowOutline {

    private String flowId;
    private OutlineCall root;

    FlowOutline(String flowId, MethodCall call, Map<String, ThreadMetadata> threads) {
        this.flowId = flowId;

        Map<ThreadMetadata, Integer> threadPos = new HashMap<>();
        call.hotspots.forEach(hotspot -> {
            ThreadMetadata thread = threads.get(hotspot.threadId);
            threadPos.put(thread, thread.stackTrace.length - 1);
        });

        root = createOutline(flowId, call, threadPos);
    }

    private OutlineCall createOutline(String flowId, MethodCall methodCall, Map<ThreadMetadata, Integer> threadPos) {
        OutlineCall res = new OutlineCall(methodCall);
        OutlineCall prepend = findCallInThreads(res, threadPos);

        if (methodCall.nestedCalls != null) {
            res.nested = new ArrayList<>();
            for (MethodCall nestedCall : methodCall.nestedCalls) {
                if (nestedCall.flows.stream().anyMatch(flow -> flow.flowId.equals(flowId))) {
                    Map<ThreadMetadata, Integer> nestedThreadPos = new HashMap<>();
                    if (nestedCall.hotspots != null) {
                        nestedCall.hotspots.forEach(hotspot -> {
                            String threadId = hotspot.threadId;
                            ThreadMetadata thread = threadPos.keySet().stream().filter(threadMetadata -> threadMetadata.getDocumentId().equals(threadId)).findFirst().get();
                            nestedThreadPos.put(thread, threadPos.get(thread));
                        });
                    }
                    res.nested.add(createOutline(flowId, nestedCall, nestedThreadPos));
                }
            }
        } else if (!threadPos.isEmpty()) {
            completeCallWithThreads(res, threadPos);
        }

        if (prepend != null) res = prepend;

        return res;
    }

    private void completeCallWithThreads(OutlineCall res, Map<ThreadMetadata, Integer> threadPos) {
        res.nested = new ArrayList<>();

        for (Map.Entry<ThreadMetadata, Integer> entry : threadPos.entrySet()) {
            StackTraceElement[] stackTrace = entry.getKey().stackTrace;
            int pos = entry.getValue();

            OutlineCall curr = res;
            for (; pos >= 0; pos--) {
                OutlineCall call = new OutlineCall(stackTrace[pos]);
                if (!curr.nested.contains(call)) curr.nested.add(call);
                curr = call;
            }
        }
    }

    private OutlineCall findCallInThreads(OutlineCall flowCall, Map<ThreadMetadata, Integer> threadPos) {
        List<OutlineCall> res = new ArrayList<>();

        // only the first thread should add to the res, others must be identical
        boolean firstThread = true;
        for (Map.Entry<ThreadMetadata, Integer> entry : threadPos.entrySet()) {
            StackTraceElement[] stackTrace = entry.getKey().stackTrace;
            int pos = entry.getValue();

            if (firstThread) {
                firstThread = false;
                boolean found = false;
                while (!found && pos >= 0) {
                    OutlineCall call = new OutlineCall(stackTrace[pos--]);
                    found = call.equals(flowCall);
                    if (!found) {
                        res.add(call);
                        if (res.size() > 1) {
                            res.get(res.size() - 2).nested.add(call);
                        }
                    }
                }

                if (found) {
                    entry.setValue(pos);
                    if (!res.isEmpty()) res.get(res.size() - 1).nested.add(flowCall);
                } else {
                    throw new RuntimeException(flowCall + " not found in thread " + entry.getKey().getDocumentId() + " from position " + entry.getValue());
                }

            } else {
                for (OutlineCall call : res) {
                    OutlineCall val = new OutlineCall(stackTrace[pos--]);
                    if (!call.equals(val)) {
                        throw new RuntimeException("stacktrace at position " + (pos + 1) + " expected " + call + " but is " + val);
                    }
                }
                entry.setValue(pos - 1);
            }
        }

        return res.isEmpty() ? null : res.get(0);
    }

    public String format() {
        return "\nflow " + flowId + formatCall("\n\t", root);
    }

    private String formatCall(String prefix, OutlineCall call) {
        String res = prefix + call.toString();
        if (call.nested != null) {
            for (OutlineCall nestedCall : call.nested) {
                res += formatCall(prefix + "\t", nestedCall);
            }
        }
        return res;
    }

    static class OutlineCall {

        String className; // external
        String methodName;

        List<OutlineCall> nested;

        OutlineCall(StackTraceElement element) {
            className = element.getClassName();
            methodName = element.getMethodName();
            nested = new ArrayList<>();
        }

        OutlineCall(MethodCall methodCall) {
            className = NameUtils.getExternalClassName(methodCall.className);
            methodName = methodCall.methodName;
        }

        @Override
        public int hashCode() {
            return className.hashCode() + 16 * methodName.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || !(obj instanceof OutlineCall)) return false;
            OutlineCall that = (OutlineCall) obj;
            return Arrays.equals(new Object[]{className, methodName}, new Object[]{that.className, that.methodName});
        }

        @Override
        public String toString() {
            return className + "." + methodName;
        }
    }
}
