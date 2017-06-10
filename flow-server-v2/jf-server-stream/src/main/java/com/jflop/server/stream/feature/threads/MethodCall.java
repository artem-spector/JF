package com.jflop.server.stream.feature.threads;

/**
 * Copy of {@link StackTraceElement}, but with public fields allowing jackson serialization/deserialization
 *
 * @author artem on 01/06/2017.
 */
public class MethodCall {

    public String declaringClass;
    public String methodName;
    public String fileName;
    public int lineNumber;

    public MethodCall() {
    }

    public MethodCall(String declaringClass, String methodName, String fileName, int lineNumber) {
        this.declaringClass = declaringClass;
        this.methodName = methodName;
        this.fileName = fileName;
        this.lineNumber = lineNumber;
    }
}
