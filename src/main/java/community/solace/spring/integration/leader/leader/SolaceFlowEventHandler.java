package community.solace.spring.integration.leader.leader;

import com.solacesystems.jcsmp.FlowEventArgs;
import com.solacesystems.jcsmp.FlowEventHandler;
import com.solacesystems.jcsmp.impl.flow.FlowHandle;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.function.Consumer;

public class SolaceFlowEventHandler implements FlowEventHandler {
    private static final Log log = LogFactory.getLog(SolaceFlowEventHandler.class);

    private final String reason;
    private final Consumer<FlowEventArgs> eventConsumer;

    public SolaceFlowEventHandler(String reason) {
        this.reason = reason;
        this.eventConsumer = null;
    }

    public SolaceFlowEventHandler(String reason, Consumer<FlowEventArgs> eventConsumer) {
        this.reason = reason;
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void handleEvent(Object source, FlowEventArgs flowEventArgs) {
        if (log.isDebugEnabled()) {
            log.debug(String.format("(%s): Received Solace Flow event [%s].", source, flowEventArgs));
        }

        if (source instanceof FlowHandle flowHandle) {
            log.info("FlowEvent(" + reason + "): " + flowEventArgs.getEvent() + ": flowId=" + flowHandle.getFlowId());
        }

        if (eventConsumer != null) {
            eventConsumer.accept(flowEventArgs);
        }
    }
}
