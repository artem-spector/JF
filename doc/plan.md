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

Status: thread dump data refactored to be raw data

Next step: 
create a raw event that represents usage of a specific thread (dumpId) and includes count of that dump.
That would allow to recognize "active" stacktraces that should be instrumented.

## 6 Dec 2016

Added thread occurrence raw data

## 8 Dec 2016

Define instrumentation metadata, that would keep track of instrumented methods.
A typical life cycle of an instrumented method is like this:

1. reported as a stack trace element of a recently active stack trace - create an entry or update last usage time
2. attempt to instrument and convert it to class + method with signature or add to black list if not instrumentable
3. snapshot dynamic tuning??
4. if last usage time is long ago, un-instrument
 
## 10 Dec 2016

Exceptions in tests caused probably by IntegratioTest that initializes a true JFlop agent, which keeps sending pings when another test clears the indexes.
Think of a feature that shits down an agent - explicitly in tests, and gradually in real life scenarios.

Instrumentation chronological sequence:

* collect instrumentable methods from the recently active thread dumps. The stacktrace elements have no method signature
* get from the client the method signatures by sending a command of "ClassInfo" feature.
  Since command-response is async, the data must be stored in the DB, for example in a "InstrumentationMetadata" document that
  for a key "agentJvm * class-method" would contain all appropriate method definitions together with other information 
  like multiple class loaders, instrumentability, etc.
* after the method signatures are available in the instrumentation metadata, put together the instrumentation configuration, 
  and send it via InstrumentationConfigurationFeature.
* when last reported client configuration available via success text of last InstrumentationConfigurationFeature command 
  is close enough to the desired configuration, send a snapshot command.

 