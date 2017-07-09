package com.jflop.server.stream.ext;

import com.jflop.server.data.AgentJVM;
import com.jflop.server.stream.base.ProcessorState;
import com.jflop.server.stream.base.StatefulProcessor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * TODO: Document!
 *
 * @author artem on 22/05/2017.
 */
public abstract class AgentProcessor<V> extends StatefulProcessor<AgentJVM, V> {

    private static final Logger logger = LoggerFactory.getLogger(AgentProcessor.class);

    protected AgentJVM agentJVM;
    private long punctuationInterval;

    @ProcessorState
    private ActiveAgentsStateStore activeAgents;

    protected AgentProcessor(String processorId, int punctuationIntervalSec) {
        super(processorId);
        if (punctuationIntervalSec >= 1) this.punctuationInterval = punctuationIntervalSec * 1000;
    }

    @Override
    public void init(ProcessorContext context) {
        super.init(context);
        agentJVM = null;
        if (punctuationInterval > 0) {
            logger.debug("scheduling " + getClass().getSimpleName() + " for " + punctuationInterval);
            context.schedule(punctuationInterval);
        }
    }

    @Override
    public void process(AgentJVM agentJVM, V v) {
        this.agentJVM = agentJVM;
    }

    @Override
    public void punctuate(long timestamp) {
        Set<AgentJVM> activeAgents = this.activeAgents.getActiveAgents(timestamp);
        logger.debug("punctuate (" + timestamp + "); processor:" + getClass().getSimpleName() + "; active agents:" + activeAgents.size());
        for (AgentJVM agentJVM : activeAgents) {
            this.agentJVM = agentJVM;
            punctuateActiveAgent(timestamp);
        }
    }

    protected abstract void punctuateActiveAgent(long timestamp);
}
