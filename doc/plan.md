# Plan

## 26 Nov 2016
We have JVMMonitorFeature reporting thread dumps every interval.
We need to:
 
 1. Based on the thread dump decide on instrumentation configuration
    If a flow can be recognized from a thread dump, and a configuration exists for that flow - use the stored configuration.   
 2. Merge the new configuration with the existing one
 3. Starting from the merged configuration refine the instrumentation configuration for the flows.
    When done, store the configuration, so that it can be retrieved on step 1.
    
Define a new class FlowMetadata, that would keep the information about a single flow, including its name, instrumentation configuration, and statistics.
This class has a method "covers" that takes a stack trace, and returns true if this flow covers that stack trace.

FlowMetadata are stored as documents in the index ProcessedDataIndex (not a good name).
They are created by DataProcessor, when a new stack trace is submitted by the agent, and no appropriate FlowMetadata exists

Status: compilation only.

Next steps: 

* define unit test to make sure the flow metadata are created, stored, retrieved, and recognized.
* for a new flow define instrumentation config
* when a snapshot is reported, recognize FlowMetadata by the snapshot and stack trace
* refine a flow

## 3 Dec 2016

In fact, flow cannot be deduced from a stack trace, because a stack trace represents only one branch of a flow.
So FlowMetadata was converted into ThreadDumpMetadata, when each document represents a unique stacktrace for an account. 

Provided a unit test in IntegrationTest.testThreadDumpMetadata.
  
Next steps:

* think of stacktrace - only analysis based on correlation of stactrace occurencies and CPU/memory usage
* choose stacktraces to instrument, and create initial instrumentation
* define relationship between stack trace,flow, and configuration
    
## 5 Dec 2016 
    
That's how the persistent data should be organized:
`jf-raw-data` index contains different types of data - CPU load, memory, stack traces, etc. All those data types have common fields: 
`time`, `dataType`, and `agentJvm`. All the raw documents are created and inserted during command data processing.
    
How to choose stacktraces to instrument:

* exclude the stacktraces that do nothing
 For example, all classes belong to java or methods are native, and the state is WAITING
 
* monitor for some time the stacktraces and choose those that repeat, 
for example it presents in every dump (or most dumps) during the monitoring interval  