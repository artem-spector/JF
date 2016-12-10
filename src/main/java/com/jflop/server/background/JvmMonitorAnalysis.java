package com.jflop.server.background;

import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.persistency.ValuePair;
import com.jflop.server.runtime.MetadataIndex;
import com.jflop.server.runtime.RawDataIndex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Set;

/**
 * Analyze raw data produced by {@link com.jflop.server.feature.JvmMonitorFeature}
 *
 * @author artem on 12/8/16.
 */
@Component
public class JvmMonitorAnalysis extends BackgroundTask {

    @Autowired
    private RawDataIndex rawDataIndex;

    @Autowired
    private MetadataIndex metadataIndex;

    public JvmMonitorAnalysis() {
        super("JVMRawDataAnalysis", 60, 3, 100);
    }

    @Override
    public void step(TaskLockData lock) {
        instrumentActiveThreads(lock.agentJvm);
    }

    private void instrumentActiveThreads(AgentJVM agentJvm) {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.add(Calendar.MINUTE, -5);
        Set<String> recentDumpIds = rawDataIndex.getRecentDumpIds(agentJvm, calendar.getTime());
        Set<ValuePair<String, String>> instrumentable = metadataIndex.getInstrumentableMethods(recentDumpIds);

        // report them to instrumentation metadata
        System.out.println("num of instrumentable methods: " +  instrumentable.size());
    }
}
