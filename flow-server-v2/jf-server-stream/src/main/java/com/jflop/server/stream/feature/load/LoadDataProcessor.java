package com.jflop.server.stream.feature.load;

import com.jflop.server.stream.base.ProcessorState;
import com.jflop.server.stream.base.SlidingWindow;
import com.jflop.server.stream.ext.AgentFeatureProcessor;
import com.jflop.server.stream.ext.CommandState;
import jsat.math.OnLineStatistics;
import org.jflop.features.JvmMonitorNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.jflop.features.JvmMonitorNames.JVM_MONITOR_FEATURE_ID;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 5/25/17
 */
public class LoadDataProcessor extends AgentFeatureProcessor implements SlidingWindow.Visitor<LoadData> {

    private static final Logger logger = LoggerFactory.getLogger(LoadDataProcessor.class);

    @ProcessorState
    private LoadDataStore loadDataStore;

    public LoadDataProcessor() {
        super(JVM_MONITOR_FEATURE_ID, 1);
    }

    @Override
    protected void processFeatureData(Map<String, ?> json) {
        logger.info("LoadDataProcessor.processFeatureData(" + json + ")");
        RawLoadData rawData = new RawLoadData();
        rawData.processCpuLoad = ((Number) json.get("processCpuLoad")).floatValue();
        Map<String, Object> heapJson = (Map<String, Object>) json.get("heapUsage");
        rawData.heapCommitted = ((Number) heapJson.get("committed")).floatValue();
        rawData.heapUsed = ((Number) heapJson.get("used")).floatValue();
        rawData.heapMax = ((Number) heapJson.get("max")).floatValue();

        loadDataStore.add(rawData);
    }

    @Override
    protected void punctuateActiveAgent(long timestamp) {
        CommandState cmd = getCommandState();
        logger.info("LoadDataProcessor.punctuate(...); command:" + cmd);
        if (cmd == null || !cmd.inProgress()) {
            logger.info("sending monitor command");
            sendCommand("monitor", null);
        } else {
            logger.info("process sliding data");
            loadDataStore.processSlidingData(3, 3, this);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public void processDataEntry(Map.Entry<Long, LoadData> value, List<Map.Entry<Long, LoadData>> prevValues, List<Map.Entry<Long, LoadData>> nextValues) {
        LoadData current = value.getValue();
        if (current.processedData != null) return;
        current.processedData = new ProcessedLoadData();

        OnLineStatistics cpuStat = new OnLineStatistics();
        for (Map.Entry<Long, LoadData> entry : prevValues) {
            cpuStat.add(entry.getValue().rawData.processCpuLoad);
        }
        cpuStat.add(current.rawData.processCpuLoad);
        for (Map.Entry<Long, LoadData> entry : nextValues) {
            cpuStat.add(entry.getValue().rawData.processCpuLoad);
        }

        current.processedData.processCpuLoadMean = (float) cpuStat.getMean();
        logger.info("calculated cpuMean " + current.rawData.processCpuLoad + "->" + current.processedData.processCpuLoadMean + "; prev: " + prevValues.size() + "; next:" + nextValues.size());

    }
}
