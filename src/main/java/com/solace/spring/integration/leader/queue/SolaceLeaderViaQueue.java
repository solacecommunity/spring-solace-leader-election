package com.solace.spring.integration.leader.queue;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.FlowEvent;
import com.solacesystems.jcsmp.FlowEventArgs;
import com.solacesystems.jcsmp.FlowEventHandler;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.InvalidOperationException;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.XMLMessageListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.function.Consumer;

public class SolaceLeaderViaQueue implements XMLMessageListener, FlowEventHandler {

    private static final Log logger = LogFactory.getLog(SolaceLeaderViaQueue.class);

    private final JCSMPSession jcsmpSession;
    private final Consumer<Boolean> eventHandler;
    private final FlowReceiver flowReceiver;

    private FlowEvent lastEvent;

    public SolaceLeaderViaQueue(JCSMPSession jcsmpSession, String queueName, Consumer<Boolean> eventHandler) throws JCSMPException {
        this.jcsmpSession = jcsmpSession;
        this.eventHandler = eventHandler;

        if (eventHandler != null) {
            eventHandler.accept(isActive());
        }

        // subscribeToQueue
        final Queue queue = provisionQueue(queueName, true, new EndpointProperties(
                EndpointProperties.ACCESSTYPE_EXCLUSIVE,
                null,
                EndpointProperties.PERMISSION_NONE,
                1
        ));

        final ConsumerFlowProperties flowProp = new ConsumerFlowProperties();
        flowProp.setEndpoint(queue);
        flowProp.setActiveFlowIndication(true); // important

        flowReceiver = jcsmpSession.createFlow(this, flowProp, null, this);
    }

    private Queue provisionQueue(String name, boolean isDurable, EndpointProperties endpointProperties)
            throws ProvisioningException {
        Queue queue;
        try {
            if (isDurable) {
                queue = JCSMPFactory.onlyInstance().createQueue(name);

                jcsmpSession.provision(queue, endpointProperties, JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);
            } else {
                // EndpointProperties will be applied during consumer creation
                queue = jcsmpSession.createTemporaryQueue(name);
            }
        } catch (JCSMPException e) {
            String action = isDurable ? "provision durable" : "create temporary";
            String msg = String.format("Failed to %s queue %s", action, name);
            logger.warn(msg, e);
            throw new ProvisioningException(msg, e);
        }

        try {
            logger.info(String.format("Testing consumer flow connection to queue %s (will not start it)", name));
            final ConsumerFlowProperties testFlowProperties = new ConsumerFlowProperties().setEndpoint(queue).setStartState(false);
            jcsmpSession.createFlow(null, testFlowProperties, endpointProperties).close();
            logger.info(String.format("Connected test consumer flow to queue %s, closing it", name));
        } catch (JCSMPException e) {
            String msg = String.format("Failed to connect test consumer flow to queue %s", name);
            if (e instanceof InvalidOperationException && !isDurable) {
                msg += ". If the Solace client is not capable of creating temporary queues, consider assigning this consumer to a group?";
            }
            logger.warn(msg, e);
            throw new ProvisioningException(msg, e);
        }

        return queue;
    }

    public void start() throws JCSMPException {
        flowReceiver.start();
    }

    public void stop() {
        flowReceiver.stop();
        lastEvent = FlowEvent.FLOW_DOWN;
    }

    public void close() {
        flowReceiver.close();
        lastEvent = FlowEvent.FLOW_DOWN;
    }

    @Override
    public void onReceive(BytesXMLMessage message) {
        // not expected to be happen
        System.out.println("Received Message:\n" + message.dump());
    }

    @Override
    public void onException(JCSMPException exception) {
        exception.printStackTrace();
    }

    public boolean isActive() {
        return FlowEvent.FLOW_ACTIVE.equals(lastEvent);
    }

    @Override
    public void handleEvent(Object source, FlowEventArgs event) {
        lastEvent = event.getEvent();

        if (eventHandler != null) {
            eventHandler.accept(isActive());
        }
    }

}
