package com.jflop.server.runtime.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

/**
 * Occurrence of the traced flow includes occurrence data of both thread and flow
 *
 * @author artem on 03/01/2017.
 */
public class TracedFlowOccurrence extends OccurrenceData {

    public static final String DATA_TYPE = "tracedFlowOccurrence";

    public String tracedMetadataId;
    public int numThreads;
    public FlowOccurrenceData.FlowElement rootFlow;

    public TracedFlowOccurrence(TracedFlowMetadata metadata, Collection<FlowOccurrenceData> flowOccurrences, Collection<ThreadOccurrenceData> threadOccurrences) {
        this.agentJvm = threadOccurrences.iterator().next().agentJvm;
        this.dataType = DATA_TYPE;
        this.time = new Date();
        tracedMetadataId = metadata.getDocumentId();

        // average of thread count
        for (ThreadOccurrenceData threadOccurrence : threadOccurrences) numThreads += threadOccurrence.count;
        numThreads = numThreads / threadOccurrences.size();

        // aggregate flow occurrences
        Collection<FlowOccurrenceData.FlowElement> flowElements = new ArrayList<>();
        for (FlowOccurrenceData flowOccurrence : flowOccurrences) {
            flowElements.add(flowOccurrence.rootFlow);
        }
        rootFlow = aggregate(metadata.rootFlow, flowElements);
    }

    @Override
    public String getMetadataId() {
        return tracedMetadataId;
    }

    private FlowOccurrenceData.FlowElement aggregate(FlowMetadata.FlowElement metadataElement, Collection<FlowOccurrenceData.FlowElement> flowElements) {
        FlowOccurrenceData.FlowElement res = new FlowOccurrenceData.FlowElement();
        res.flowId = metadataElement.flowId;
        res.minTime = Long.MAX_VALUE;
        FlowMetadata.FlowElement subflow = (metadataElement.subflows == null || metadataElement.subflows.isEmpty()) ? null : metadataElement.subflows.get(0);

        Collection<FlowOccurrenceData.FlowElement> subelements = new ArrayList<>();
        for (FlowOccurrenceData.FlowElement element : flowElements) {
            res.count += element.count;
            res.cumulativeTime += element.cumulativeTime;
            res.maxTime = Math.max(res.maxTime, element.maxTime);
            res.minTime = Math.min(res.minTime, element.minTime);

            if (subflow != null) {
                for (FlowOccurrenceData.FlowElement subelement : element.subflows) {
                    if (subelement.flowId.equals(subflow.flowId)) subelements.add(subelement);
                }
            }
        }

        if (subflow != null) {
            res.subflows = Collections.singletonList(aggregate(subflow, subelements));
        }

        return res;
    }
}