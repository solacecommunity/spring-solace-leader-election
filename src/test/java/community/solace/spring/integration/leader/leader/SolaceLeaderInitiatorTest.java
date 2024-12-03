package community.solace.spring.integration.leader.leader;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;

import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.FlowEvent;
import com.solacesystems.jcsmp.FlowEventHandler;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.SpringJCSMPFactory;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.impl.flow.FlowEventArgsImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.leader.event.OnRevokedEvent;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SolaceLeaderInitiatorTest {

    private final String ROLE = "testGroup";

    private JCSMPSession session;
    private SolaceLeaderInitiator solaceLeaderInitiator;
    private ApplicationEventPublisher eventPublisher;
    private final SolaceLeaderConfig leaderConfig = new SolaceLeaderConfig();

    @Before
    public void setUp() throws Exception {
        session = mock(JCSMPSession.class);

        eventPublisher = mock(ApplicationEventPublisher.class);

        leaderConfig.setJoinGroups(new ArrayList<>());
        leaderConfig.setPermitAnonymousGroups(true);

        setUpSolaceLeaderInitiator();

        FlowReceiver flowReceiverForTest = mock(FlowReceiver.class);
        when(session.createFlow(
                isNull(),
                any(ConsumerFlowProperties.class),
                any(),
                any()
        )).thenReturn(flowReceiverForTest);
    }

    private void setUpSolaceLeaderInitiator() throws JCSMPException {
        SpringJCSMPFactory springJCSMPFactory = mock(SpringJCSMPFactory.class);
        when(springJCSMPFactory.createSession()).thenReturn(session);

        solaceLeaderInitiator = new SolaceLeaderInitiator(springJCSMPFactory.createSession(), leaderConfig, null);
        solaceLeaderInitiator.setApplicationEventPublisher(eventPublisher);
    }

    @Test
    public void joinGroup_isLeader() throws JCSMPException, InterruptedException {
        ArgumentCaptor<FlowEventHandler> flowEventHandlerCaptor = ArgumentCaptor.forClass(FlowEventHandler.class);
        Candidate candidate = joinGroup(ROLE, flowEventHandlerCaptor);

        // Fire up event
        flowEventHandlerCaptor.getValue().handleEvent(null, new FlowEventArgsImpl(FlowEvent.FLOW_ACTIVE, null, null, 0));

        // Verify that candidate get the grant.
        verify(candidate, atMostOnce()).onRevoked(any(Context.class));
        verify(eventPublisher, atMostOnce()).publishEvent(any(OnRevokedEvent.class));
        verify(candidate, times(1)).onGranted(any(Context.class));
        verify(eventPublisher).publishEvent(any(OnGrantedEvent.class));
        Assert.assertTrue(solaceLeaderInitiator.getContext(ROLE).isLeader());
    }

    @Test
    public void joinGroup_isNotLeader() throws JCSMPException, InterruptedException {
        ArgumentCaptor<FlowEventHandler> flowEventHandlerCaptor = ArgumentCaptor.forClass(FlowEventHandler.class);
        Candidate candidate = joinGroup(ROLE, flowEventHandlerCaptor);

        // Fire INACTIVE event
        flowEventHandlerCaptor.getValue().handleEvent(null, new FlowEventArgsImpl(FlowEvent.FLOW_INACTIVE, null, null, 0));

        // Verify that candidate get the grant.
        verify(candidate, atLeastOnce()).onRevoked(any(Context.class));
        verify(eventPublisher, atLeastOnce()).publishEvent(any(OnRevokedEvent.class));
        verify(candidate, never()).onGranted(any(Context.class));
        verify(eventPublisher, never()).publishEvent(any(OnGrantedEvent.class));
        Assert.assertFalse(solaceLeaderInitiator.getContext(ROLE).isLeader());
    }

    @Test
    public void joinGroup_yield() throws JCSMPException, InterruptedException {
        ArgumentCaptor<FlowEventHandler> flowEventHandlerCaptor = ArgumentCaptor.forClass(FlowEventHandler.class);
        Candidate candidate = joinGroup(ROLE, flowEventHandlerCaptor);

        // Fire up event
        flowEventHandlerCaptor.getValue().handleEvent(null, new FlowEventArgsImpl(FlowEvent.FLOW_ACTIVE, null, null, 0));

        // Verify that candidate get the grant.
        verify(candidate, atMostOnce()).onRevoked(any(Context.class));
        verify(eventPublisher, atMostOnce()).publishEvent(any(OnRevokedEvent.class));
        verify(candidate, times(1)).onGranted(any(Context.class));
        verify(eventPublisher).publishEvent(any(OnGrantedEvent.class));
        Assert.assertTrue(solaceLeaderInitiator.getContext(ROLE).isLeader());

        // I dont like to be the leader.
        reset(candidate);
        reset(eventPublisher);
        solaceLeaderInitiator.getContext(ROLE).yield();

        // Verify that we dont be the leader.
        verify(candidate, atLeastOnce()).onRevoked(any(Context.class));
        verify(eventPublisher, atLeastOnce()).publishEvent(any(OnRevokedEvent.class));
        verify(candidate, never()).onGranted(any(Context.class));
        verify(eventPublisher, never()).publishEvent(any(OnGrantedEvent.class));
        Assert.assertFalse(solaceLeaderInitiator.getContext(ROLE).isLeader());
    }

    @Test
    public void joinGroup_isLeader_lostConnectionToBroker() throws JCSMPException, InterruptedException {
        ArgumentCaptor<FlowEventHandler> flowEventHandlerCaptor = ArgumentCaptor.forClass(FlowEventHandler.class);
        Candidate candidate = joinGroup(ROLE, flowEventHandlerCaptor);

        // Fire FLOW_ACTIVE event
        flowEventHandlerCaptor.getValue().handleEvent(null, new FlowEventArgsImpl(FlowEvent.FLOW_ACTIVE, null, null, 0));

        // Verify that candidate get the grant.
        verify(candidate, atMostOnce()).onRevoked(any(Context.class));
        verify(candidate, times(1)).onGranted(any(Context.class));

        Assert.assertTrue(solaceLeaderInitiator.getContext(ROLE).isLeader());

        reset(candidate);
        reset(eventPublisher);

        // Fire DOWN event
        flowEventHandlerCaptor.getValue().handleEvent(null, new FlowEventArgsImpl(FlowEvent.FLOW_DOWN, null, null, 0));

        // Verify that candidate get the grant.
        verify(candidate, atLeastOnce()).onRevoked(any(Context.class));
        verify(candidate, never()).onGranted(any(Context.class));

        Assert.assertFalse(solaceLeaderInitiator.getContext(ROLE).isLeader());
    }

    /**
     * A "role" is unique and a {@link org.springframework.integration.leader.Candidate} containing on* methods.
     * Those on* methods can only registered once.
     */
    @Test(expected = IllegalArgumentException.class)
    public void joinGroup_calledTwice_shouldThrowException() throws JCSMPException {
        ArgumentCaptor<FlowEventHandler> flowEventHandlerCaptor = ArgumentCaptor.forClass(FlowEventHandler.class);

        joinGroup(ROLE, flowEventHandlerCaptor);
        joinGroup(ROLE, flowEventHandlerCaptor);
    }

    /**
     * A "role" is unique and a {@link org.springframework.integration.leader.Candidate} containing on* methods.
     * Those on* methods can only registered once.
     */
    @Test(expected = IllegalArgumentException.class)
    public void joinGroup_isAnonymous_shouldThrowException() throws Exception {
        leaderConfig.setPermitAnonymousGroups(false);
        setUpSolaceLeaderInitiator();

        ArgumentCaptor<FlowEventHandler> flowEventHandlerCaptor = ArgumentCaptor.forClass(FlowEventHandler.class);

        joinGroup(ROLE, flowEventHandlerCaptor);
    }

    @Test
    public void testGetContext_withJoinedQueue() throws JCSMPException {
        ArgumentCaptor<FlowEventHandler> flowEventHandlerCaptor = ArgumentCaptor.forClass(FlowEventHandler.class);

        joinGroup(ROLE, flowEventHandlerCaptor);


        Context context = solaceLeaderInitiator.getContext(ROLE);

        // At beginning we are no leader
        Assert.assertFalse(context.isLeader());

        // Fire FLOW_ACTIVE event
        flowEventHandlerCaptor.getValue().handleEvent(null, new FlowEventArgsImpl(FlowEvent.FLOW_ACTIVE, null, null, 0));

        Assert.assertTrue(context.isLeader());
    }

    @Test
    public void testGetContext_autoJoinQueue_Programmatic() throws JCSMPException {
        ArgumentCaptor<FlowEventHandler> flowEventHandlerCaptor = ArgumentCaptor.forClass(FlowEventHandler.class);
        FlowReceiver flowReceiver = mockFlow(flowEventHandlerCaptor);

        // NO "solaceLeaderInitiator.joinGroup(candidate);" before:
        Context context = solaceLeaderInitiator.getContext(ROLE, true);
        Assert.assertNotNull(context);
        Assert.assertFalse(context.isLeader());

        // Verify that the receiver was started.
        verify(flowReceiver).start();
        verify(flowReceiver, never()).stop();

        // Fire FLOW_ACTIVE event
        flowEventHandlerCaptor.getValue().handleEvent(null, new FlowEventArgsImpl(FlowEvent.FLOW_ACTIVE, null, null, 0));

        Assert.assertTrue(context.isLeader());
    }

    @Test
    public void testGetContext_autoJoinQueue_ViaConfig() throws Exception {
        ArgumentCaptor<FlowEventHandler> flowEventHandlerCaptor = ArgumentCaptor.forClass(FlowEventHandler.class);
        FlowReceiver flowReceiver = mockFlow(flowEventHandlerCaptor);

        setLeaderGroupJoinType(ROLE, SolaceLeaderConfig.LEADER_GROUP_JOIN.FIRST_USE);

        // NO "solaceLeaderInitiator.joinGroup(candidate);" before:
        Context context = solaceLeaderInitiator.getContext(ROLE);
        Assert.assertNotNull(context);
        Assert.assertFalse(context.isLeader());

        // Verify that the receiver was started.
        verify(flowReceiver).start();
        verify(flowReceiver, never()).stop();

        // Fire FLOW_ACTIVE event
        flowEventHandlerCaptor.getValue().handleEvent(null, new FlowEventArgsImpl(FlowEvent.FLOW_ACTIVE, null, null, 0));

        Assert.assertTrue(context.isLeader());
    }

    @Test
    public void testGetContext_autoJoinQueue_ViaConfigNegative() throws Exception {
        ArgumentCaptor<FlowEventHandler> flowEventHandlerCaptor = ArgumentCaptor.forClass(FlowEventHandler.class);
        mockFlow(flowEventHandlerCaptor);

        setLeaderGroupJoinType(ROLE, SolaceLeaderConfig.LEADER_GROUP_JOIN.PROGRAMMATIC);

        Context context = solaceLeaderInitiator.getContext(ROLE);
        Assert.assertNull(context);
    }

    @Test
    public void testGetContext_joinQueueViaLeaderEvent() throws Exception {
        ArgumentCaptor<FlowEventHandler> flowEventHandlerCaptor = ArgumentCaptor.forClass(FlowEventHandler.class);
        FlowReceiver flowReceiver = mockFlow(flowEventHandlerCaptor);

        setLeaderGroupJoinType(ROLE, SolaceLeaderConfig.LEADER_GROUP_JOIN.ON_READINESS);

        Context context;
        context = solaceLeaderInitiator.getContext(ROLE);
        Assert.assertNull(context);

        solaceLeaderInitiator.onApplicationReadyEvent(new ApplicationReadyEvent(mock(SpringApplication.class), null, null, Duration.ofSeconds(1)));

        context = solaceLeaderInitiator.getContext(ROLE);
        Assert.assertNotNull(context);
        Assert.assertFalse(context.isLeader());

        // Verify that the receiver was started.
        verify(flowReceiver).start();
        verify(flowReceiver, never()).stop();

        // Fire FLOW_ACTIVE event
        flowEventHandlerCaptor.getValue().handleEvent(null, new FlowEventArgsImpl(FlowEvent.FLOW_ACTIVE, null, null, 0));

        Assert.assertTrue(context.isLeader());
    }


    @Test
    public void testGetContext_autoJoinQueue_ViaConfigNegativeDefault() throws JCSMPException {
        ArgumentCaptor<FlowEventHandler> flowEventHandlerCaptor = ArgumentCaptor.forClass(FlowEventHandler.class);
        mockFlow(flowEventHandlerCaptor);

        // NO "solaceLeaderInitiator.joinGroup(candidate);" before:
        Context context = solaceLeaderInitiator.getContext(ROLE);
        Assert.assertNull(context);
    }

    private FlowReceiver mockFlow(ArgumentCaptor<FlowEventHandler> flowEventHandlerCaptor) throws JCSMPException {
        FlowReceiver flowReceiver = mock(FlowReceiver.class);
        when(session.createFlow(
                any(XMLMessageListener.class),
                any(ConsumerFlowProperties.class),
                isNull(),
                flowEventHandlerCaptor.capture()
        )).thenReturn(flowReceiver);

        return flowReceiver;
    }

    private Candidate createCandidate(String role) {
        Candidate candidate = mock(Candidate.class);
        when(candidate.getRole()).thenReturn(role);

        return candidate;
    }

    private Candidate joinGroup(@SuppressWarnings("SameParameterValue") String role, ArgumentCaptor<FlowEventHandler> flowEventHandlerCaptor) throws JCSMPException {
        FlowReceiver flowReceiver = mockFlow(flowEventHandlerCaptor);
        Candidate candidate = createCandidate(role);

        solaceLeaderInitiator.joinGroup(candidate, false, false);

        // Verify that the receiver was started.
        verify(flowReceiver).start();
        verify(flowReceiver, never()).stop();

        return candidate;
    }

    private void setLeaderGroupJoinType(String role, SolaceLeaderConfig.LEADER_GROUP_JOIN joinType) throws Exception {
        JoinGroupConfig config = new JoinGroupConfig();
        config.setGroupName(role);
        config.setJoinType(joinType);
        leaderConfig.setJoinGroups(Collections.singletonList(config));
        setUpSolaceLeaderInitiator();
    }
}
