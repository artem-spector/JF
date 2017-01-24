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

    public String format(boolean removeClutter) {
        if (removeClutter) {
            Set<OutlineCall> clutter = getClutter();
            removeClutter(root, clutter);
        }
        return "\nflow " + flowId + formatCall("\n\t", root);
    }

    private void removeClutter(OutlineCall call, Set<OutlineCall> clutter) {
        if (call.nested == null || call.nested.isEmpty() || clutter.isEmpty()) return;

        OutlineCall copy = call.shallowCopy();
        for (OutlineCall nestedCall : call.nested) {
            removeClutter(nestedCall, clutter);
            if (clutter.contains(nestedCall)) {
                if (nestedCall.nested != null) {
                    for (OutlineCall grandchild : nestedCall.nested) {
                        copy.addNested(grandchild);
                    }
                }
            } else {
                copy.addNested(nestedCall);
            }
        }

        call.nested = copy.nested;
    }

    private Set<OutlineCall> getClutter() {
        Map<OutlineCall, Integer> callCounts = new HashMap<>();
        getCallUsage(root, callCounts);

        List<OutlineCall> sortedCalls = new ArrayList<>(callCounts.keySet());
        sortedCalls.sort(Comparator.comparingInt(callCounts::get).reversed());

        Set<OutlineCall> clutter = new HashSet<>();
        int maxCount = callCounts.get(sortedCalls.get(0));
        int minCount = callCounts.get(sortedCalls.get(sortedCalls.size() - 1));
        for (OutlineCall call : sortedCalls) {
            int count = callCounts.get(call);
            if (maxCount - count < count - minCount) {
                clutter.add(call);
            } else
                break;
        }

        return clutter;
    }

    private int getCallUsage(OutlineCall call, Map<OutlineCall, Integer> res) {
        int totalNum = 1;
        res.compute(call, (key, count) -> count == null ? 1 : count + 1);
        if (call.nested != null) {
            for (OutlineCall nestedCall : call.nested) {
                totalNum += getCallUsage(nestedCall, res);
            }
        }
        return totalNum;
    }

    private OutlineCall createOutline(String flowId, MethodCall methodCall, Map<ThreadMetadata, Integer> threadPos) {
        OutlineCall res = new OutlineCall(methodCall);
        OutlineCall prepend = findCallInThreads(res, threadPos);

        if (methodCall.nestedCalls != null) {
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
                    res.addNested(createOutline(flowId, nestedCall, nestedThreadPos));
                }
            }
        } else if (!threadPos.isEmpty()) {
            completeCallWithThreads(res, threadPos);
        }

        if (prepend != null) res = prepend;

        return res;
    }

    private void completeCallWithThreads(OutlineCall res, Map<ThreadMetadata, Integer> threadPos) {
        for (Map.Entry<ThreadMetadata, Integer> entry : threadPos.entrySet()) {
            StackTraceElement[] stackTrace = entry.getKey().stackTrace;
            int pos = entry.getValue();

            OutlineCall curr = res;
            for (; pos >= 0; pos--) {
                OutlineCall call = new OutlineCall(stackTrace[pos]);
                curr.addNested(call);
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
                            res.get(res.size() - 2).addNested(call);
                        }
                    }
                }

                if (found) {
                    entry.setValue(pos);
                    if (!res.isEmpty()) res.get(res.size() - 1).addNested(flowCall);
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

        OutlineCall() {
        }

        OutlineCall(StackTraceElement element) {
            className = element.getClassName();
            methodName = element.getMethodName();
        }

        OutlineCall(MethodCall methodCall) {
            className = NameUtils.getExternalClassName(methodCall.className);
            methodName = methodCall.methodName;
        }

        OutlineCall addNested(OutlineCall nestedCall) {
            if (nested == null) nested = new ArrayList<>();
            int pos = nested.indexOf(nestedCall);
            if (pos == -1) {
                nested.add(nestedCall);
                return nestedCall;
            } else {
                return nested.get(pos);
            }
        }

        OutlineCall shallowCopy() {
            OutlineCall res = new OutlineCall();
            res.className = className;
            res.methodName = methodName;
            return res;
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
