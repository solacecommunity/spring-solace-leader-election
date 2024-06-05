package community.solace.spring.integration.leader.leader;


import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.SpringJCSMPFactory;
import community.solace.spring.integration.leader.leader.SolaceLeaderConfig.LEADER_GROUP_JOIN;
import community.solace.spring.integration.leader.queue.ProvisioningException;
import community.solace.spring.integration.leader.queue.SolaceLeaderViaQueue;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.event.EventListener;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.DefaultCandidate;
import org.springframework.integration.leader.event.DefaultLeaderEventPublisher;
import org.springframework.integration.leader.event.LeaderEventPublisher;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

/**
 * Bootstrap leadership {@link org.springframework.integration.leader.Candidate candidates}
 * with Solace.
 * <p>
 * Mention that your queue failover timeout is configured at:
 * (configure/client-profile/service)# min-keepalive-timeout 10
 * on your broker.
 */
@Component
@ManagedResource()
public class SolaceLeaderInitiator implements ApplicationEventPublisherAware {

    private static final Log logger = LogFactory.getLog(SolaceLeaderInitiator.class);
    private static final String SOLACE_GROUP_PREFIX = "leader.";
    private final JCSMPSession session;
    private final Map<String, LeaderGroupContainer> leaderGroups = new HashMap<>();
    private final Map<String, LEADER_GROUP_JOIN> joinGroupsConfig;
    private final Set<String> yieldOnShutdownConfig;
    private final boolean anonymousGroupsArePermitted;
    private final ApplicationContext appContext;

    private final SolaceLeaderHealthIndicator solaceLeaderHealthIndicator;

    /**
     * Leader event publisher.
     */
    private volatile LeaderEventPublisher leaderEventPublisher = new DefaultLeaderEventPublisher();

    public SolaceLeaderInitiator(SpringJCSMPFactory solaceFactory, SolaceLeaderConfig solaceLeaderConfig, ApplicationContext appContext,  SolaceLeaderHealthIndicator solaceLeaderHealthIndicator) {
        this.joinGroupsConfig = SolaceLeaderConfig.getJoinGroupMap(solaceLeaderConfig);
        this.yieldOnShutdownConfig = SolaceLeaderConfig.getYieldOnShutdown(solaceLeaderConfig);
        this.anonymousGroupsArePermitted = solaceLeaderConfig.isPermitAnonymousGroups();
        this.solaceLeaderHealthIndicator = solaceLeaderHealthIndicator;
        solaceLeaderHealthIndicator.down();

        try {
            this.session = solaceFactory.createSession();
        }
        catch (InvalidPropertiesException e) {
            throw new IllegalArgumentException("Missing solace broker configuration, for leader election", e);
        }

        this.appContext = appContext;
        solaceLeaderHealthIndicator.up();

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownHook));
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.leaderEventPublisher = new DefaultLeaderEventPublisher(applicationEventPublisher);
    }

    public void joinGroup(String groupName) {
        joinGroup(groupName, true);
    }

    @ManagedOperation(description = "Join a leader group")
    public void joinGroup(String groupName, boolean yieldOnShutdown) {
        joinGroup(groupName, false, yieldOnShutdown);
    }

    private void joinGroup(String groupName, boolean ignoreExisting, boolean yieldOnShutdown) {
        joinGroup(new DefaultCandidate(UUID.randomUUID().toString(), groupName), ignoreExisting, yieldOnShutdown);
    }

    @SuppressWarnings("unused")
    void joinGroup(Candidate candidate, boolean ignoreExisting, boolean yieldOnShutdown) {
        if (leaderGroups.containsKey(candidate.getRole())) {
            if (leaderGroups.get(candidate.getRole()).getContext().isJoined() && !ignoreExisting) {
                throw new IllegalArgumentException("A candidate with groupName \"" +
                        candidate.getRole() +
                        "\" was already joined");
            }

            leaderGroups.get(candidate.getRole()).join();
        }
        else {
            if (!joinGroupsConfig.containsKey(candidate.getRole())
                    && !anonymousGroupsArePermitted) {
                throw new IllegalArgumentException("The groupName \"" +
                        candidate.getRole() +
                        "\" is not defined in your configuration at: spring.leader.join-groups. And spring.leader.permit-anonymous-groups = false.");
            }

            registerCandidate(candidate, yieldOnShutdown)
                    .join();
        }
    }

    private LeaderGroupContainer registerCandidate(Candidate candidate, boolean yieldOnShutdown) {
        LeaderGroupContainer container = new LeaderGroupContainer(candidate, yieldOnShutdown);
        leaderGroups.put(candidate.getRole(), container);

        return container;
    }

    public Context getContext(final String groupName) {
        LEADER_GROUP_JOIN groupJoinType = joinGroupsConfig
                .getOrDefault(groupName, LEADER_GROUP_JOIN.PROGRAMMATIC);
        boolean autoJoin = LEADER_GROUP_JOIN.FIRST_USE.equals(groupJoinType);

        return getContext(groupName, autoJoin);
    }

    public Context getContext(final String groupName, final boolean autoJoin) {
        LeaderGroupContainer leaderGroup = leaderGroups.get(groupName);
        if (leaderGroup == null) {
            if (autoJoin) {
                joinGroup(groupName);
                leaderGroup = leaderGroups.get(groupName);
            }
            else {
                return null;
            }

        }
        return leaderGroup.getContext();
    }

    @ManagedAttribute(description = "List of all leader groups and the current status", currencyTimeLimit = 1)
    public Collection<String> getLeaderStatus() {
        Map<String, String> status = leaderGroups.values().stream()
                .map(LeaderGroupContainer::getContext)
                .collect(Collectors.toMap(
                        SolaceContext::getRole,
                        c -> c.isLeader() ? "leader" : "not leader"
                ));

        for (String definedRole : this.joinGroupsConfig.keySet()) {
            status.putIfAbsent(definedRole, "not joined");
        }

        // Convert to pretty printed table.
        int keyColumnWidth = status.keySet().stream().mapToInt(String::length).max().orElse(0) + 2;
        return status.entrySet().stream()
                .map(eS -> String.format("%1$-" + keyColumnWidth + "s", eS.getKey() + ": ") + eS.getValue())
                .collect(Collectors.toList());
    }

    @ManagedOperation(description = "yield the leadership of the given group")
    public void yieldLeaderShip(String groupName) {
        Context context = getContext(groupName, false);
        if (context != null) {
            context.yield();
        }
    }

    // Register all defined groups early to create micrometer instance.
    @EventListener
    public void onApplicationStartedEvent(ApplicationStartedEvent event) {
        if (!Objects.equals(appContext, event.getApplicationContext())) {
            // Workaround for: https://github.com/spring-cloud/spring-cloud-stream/issues/2083
            return;
        }
        for (Map.Entry<String, LEADER_GROUP_JOIN> groupToJoin : joinGroupsConfig.entrySet()) {
            if (!leaderGroups.containsKey(groupToJoin.getKey())) {
                DefaultCandidate candidate = new DefaultCandidate(UUID.randomUUID().toString(), groupToJoin.getKey());

                registerCandidate(candidate, yieldOnShutdownConfig.contains(groupToJoin.getKey()));
            }
        }
    }

    @EventListener
    public void onApplicationReadyEvent(ApplicationReadyEvent event) {
        if (!Objects.equals(appContext, event.getApplicationContext())) {
            // Workaround for: https://github.com/spring-cloud/spring-cloud-stream/issues/2083
            return;
        }
        for (Map.Entry<String, LEADER_GROUP_JOIN> groupToJoin : joinGroupsConfig.entrySet()) {
            if (LEADER_GROUP_JOIN.ON_READINESS.equals(groupToJoin.getValue())) {
                joinGroup(groupToJoin.getKey(), true, yieldOnShutdownConfig.contains(groupToJoin.getKey()));
            }
        }
    }

    public boolean hasJoinGroupsConfig(String groupName) {
        return joinGroupsConfig.containsKey(groupName);
    }

    private void shutdownHook() {
        for (LeaderGroupContainer container : leaderGroups.values()) {
            if (container.getContext().isLeader() && container.getContext().shouldYieldOnShutdown()) {
                logger.info("shutdown hook executed, yielding leader ship of: " + container.getContext().getRole());
                container.getContext().yield();
            }
        }
    }

    private class LeaderGroupContainer {
        private final Candidate candidate;
        private SolaceContext context;
        private SolaceLeaderViaQueue elector;

        private LeaderGroupContainer(Candidate candidate, boolean yieldOnShutdown) {
            this.candidate = candidate;

            context = new SolaceContext(candidate, () -> {
                try {
                    if (elector != null) {
                        elector.stop();

                        context.setLeader(false);
                        candidate.onRevoked(context);
                        leaderEventPublisher.publishOnRevoked(SolaceLeaderInitiator.this, context, candidate.getRole());

                        elector.start();
                    }
                }
                catch (JCSMPException e) {
                    logger.error("yield failed: unable to start the flow. Your will never be the leader.", e);
                    leaderEventPublisher
                            .publishOnFailedToAcquire(SolaceLeaderInitiator.this, context, candidate.getRole());
                }
            }, yieldOnShutdown);

            Gauge.builder(
                            "leader_status",
                            this,
                            lgc -> lgc.getContext().getGaugeValue()
                    )
                    .description("Indicates if this project is [-1=not joined, 0=joined but not leader, 1=is leader] for a group.")
                    .tag("group", candidate.getRole())
                    .strongReference(true)
                    .register(Metrics.globalRegistry);
        }

        private synchronized void join() {
            if (context.isJoined()) {
                return;
            }

            try {
                elector = new SolaceLeaderViaQueue(
                        session,
                        SOLACE_GROUP_PREFIX + candidate.getRole(),
                        active -> {
                            context.setLeader(active);

                            if (active) {
                                try {
                                    candidate.onGranted(context);
                                    leaderEventPublisher
                                            .publishOnGranted(SolaceLeaderInitiator.this, context, candidate.getRole());
                                }
                                catch (InterruptedException e) {
                                    logger.error("Unable to tell candidate that leader was granted.");
                                }
                            }
                            else {
                                candidate.onRevoked(context);
                                leaderEventPublisher
                                        .publishOnRevoked(SolaceLeaderInitiator.this, context, candidate.getRole());
                            }
                        },
                        solaceLeaderHealthIndicator::down
                );
                context.setJoined();
            }
            catch (ProvisioningException e) {
                logger.error("Unable to bind queue \"" + candidate
                        .getRole() + "\". Your have to create the queue manually", e);
                leaderEventPublisher.publishOnFailedToAcquire(SolaceLeaderInitiator.this, context, candidate.getRole());
            }

            try {
                elector.start();
            }
            catch (JCSMPException e) {
                logger.error("Unable to start the flow. Your will never be the leader.", e);
                leaderEventPublisher.publishOnFailedToAcquire(SolaceLeaderInitiator.this, context, candidate.getRole());
            }
        }

        public SolaceContext getContext() {
            return context;
        }
    }
}
