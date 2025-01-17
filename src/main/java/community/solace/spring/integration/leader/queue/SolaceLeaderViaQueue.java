package community.solace.spring.integration.leader.queue;

import com.solacesystems.jcsmp.*;
import community.solace.spring.integration.leader.leader.SolaceFlowEventHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.function.Consumer;

public class SolaceLeaderViaQueue implements XMLMessageListener {

    private static final Log logger = LogFactory.getLog(SolaceLeaderViaQueue.class);

    private final JCSMPSession jcsmpSession;
    private final Consumer<Boolean> eventHandler;
    private final Consumer<Throwable> onError;
    private final ConsumerFlowProperties flowProp;

    private FlowReceiver flowReceiver;

    private FlowEvent lastEvent;

    public SolaceLeaderViaQueue(JCSMPSession jcsmpSession, String queueName, Consumer<Boolean> eventHandler, Consumer<Throwable> onError) {
        this.jcsmpSession = jcsmpSession;
        this.eventHandler = eventHandler;
        this.onError = onError;

        if (eventHandler != null) {
            eventHandler.accept(isActive());
        }

        // subscribeToQueue
        final Queue queue = provisionQueue(queueName, new EndpointProperties(
                EndpointProperties.ACCESSTYPE_EXCLUSIVE,
                null,
                EndpointProperties.PERMISSION_NONE,
                1
        ));

        flowProp = new ConsumerFlowProperties();
        flowProp.setEndpoint(queue);
        flowProp.setActiveFlowIndication(true); // important
    }

    private Queue provisionQueue(String name, EndpointProperties endpointProperties)
            throws ProvisioningException {
        Queue queue;
        try {
            queue = JCSMPFactory.onlyInstance().createQueue(name);

            jcsmpSession.provision(queue, endpointProperties, JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);

        } catch (JCSMPException e) {
            String msg = String.format("Failed to provision durable queue %s", name);
            logger.warn(msg, e);
            throw new ProvisioningException(msg, e);
        }

        try {
            logger.info(String.format("Testing consumer flow connection to queue %s (will not start it)", name));
            final ConsumerFlowProperties testFlowProperties = new ConsumerFlowProperties().setEndpoint(queue).setStartState(false);
            jcsmpSession.createFlow(null, testFlowProperties, endpointProperties, new SolaceFlowEventHandler("Testing consumer")).close();
            logger.info(String.format("Connected test consumer flow to queue %s, closing it", name));
        } catch (JCSMPException e) {
            String msg = String.format("Failed to connect test consumer flow to queue %s", name);
            logger.warn(msg, e);
            throw new ProvisioningException(msg, e);
        }

        return queue;
    }

    public void start(String candidateName) throws JCSMPException {
        flowReceiver = jcsmpSession.createFlow(
                new XMLMessageListener() {

                    @Override
                    public void onReceive(BytesXMLMessage xmlMessage) {
                        // Ignore. Her should never arrive any messages. Being a black hole is ok.
                    }

                    @Override
                    public void onException(JCSMPException e) {
                        onError.accept(e);
                    }
                },
                flowProp,
                null,
                new SolaceFlowEventHandler("leader." + candidateName, (FlowEventArgs event) -> {
                    logger.debug("SolaceLeader: received event: " + event);
                    lastEvent = event.getEvent();

                    if (eventHandler != null) {
                        eventHandler.accept(isActive());
                    }
                })
        );
        flowReceiver.start();
    }

    public void stop() {
        flowReceiver.close();
        flowReceiver = null;
        lastEvent = FlowEvent.FLOW_DOWN;
    }

    @Override
    public void onReceive(BytesXMLMessage message) {
        // not expected to be happen
        logger.warn("SolaceLeader: Received unexpected message:\n" + message.dump());
    }

    @Override
    public void onException(JCSMPException exception) {
        logger.error("MessageHandlerError", exception);
    }

    public boolean isActive() {
        return FlowEvent.FLOW_ACTIVE.equals(lastEvent);
    }

}
