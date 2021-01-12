package com.solace.spring.integration.leader.leader;

import com.solace.spring.integration.leader.queue.ProvisioningException;
import com.solace.spring.integration.leader.queue.SolaceLeaderViaQueue;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.SpringJCSMPFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.event.DefaultLeaderEventPublisher;
import org.springframework.integration.leader.event.LeaderEventPublisher;

import java.util.HashMap;
import java.util.Map;

/**
 * Bootstrap leadership {@link org.springframework.integration.leader.Candidate candidates}
 * with Solace. Upon construction, {@link #start} must be invoked to
 * register the candidate for leadership election.
 * <p>
 * Mention, that your queue failover timeout is configured at:
 * (configure/client-profile/service)# min-keepalive-timeout 10
 * on your broker.
 */
public class SolaceLeaderInitiator implements SmartLifecycle, DisposableBean, ApplicationEventPublisherAware {

    private static final Log logger = LogFactory.getLog(SolaceLeaderInitiator.class);

    /**
     * Leader event publisher.
     */
    private volatile LeaderEventPublisher leaderEventPublisher = new DefaultLeaderEventPublisher();
    private volatile JCSMPSession session;
    private volatile Map<String, LeaderGroupContainer> leaderGroups = new HashMap<>();
    private volatile boolean running;


    public SolaceLeaderInitiator(SpringJCSMPFactory solaceFactory) {
        try {
            this.session = solaceFactory.createSession();
        } catch (InvalidPropertiesException e) {
            throw new IllegalArgumentException("Missing solace broker configuration, for leader election", e);
        }
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.leaderEventPublisher = new DefaultLeaderEventPublisher(applicationEventPublisher);
    }

    @Override
    public synchronized void start() {
        if (!this.running) {
            for (LeaderGroupContainer leaderGroup : leaderGroups.values()) {
                leaderGroup.init();
            }

            this.running = true;
        }
    }

    @Override
    public synchronized void stop() {
        if (this.running) {
            for (LeaderGroupContainer leaderGroup : leaderGroups.values()) {
                leaderGroup.destroy();
            }

            this.running = false;
        }
    }

    @Override
    public void destroy() {
        stop();
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    public void joinGroup(Candidate candidate) {
        if (leaderGroups.containsKey(candidate.getRole())) {
            throw new IllegalArgumentException("A candidate with role \"" + candidate.getRole() + "\" was already registered");
        }

        LeaderGroupContainer container = new LeaderGroupContainer(candidate);
        leaderGroups.put(candidate.getRole(), container);

        if (isRunning()) {
            container.init();
        }
    }

    public Context getContext(final String role) {
        LeaderGroupContainer leaderGroup = leaderGroups.get(role);
        if (leaderGroup == null) {
            return null;
        }
        return leaderGroup.getContext();
    }

    private class LeaderGroupContainer {
        private final Candidate candidate;
        private SolaceContext context;
        private SolaceLeaderViaQueue elector;

        private LeaderGroupContainer(Candidate candidate) {
            this.candidate = candidate;
        }

        public void init() {
            context = new SolaceContext(candidate, () -> {
                try {
                    elector.stop();
                    elector.start();
                } catch (JCSMPException e) {
                    logger.error("yield failed: unable to start the flow. Your will never be the leader.", e);
                    leaderEventPublisher.publishOnFailedToAcquire(SolaceLeaderInitiator.this, context, candidate.getRole());
                }
            });

            try {
                elector = new SolaceLeaderViaQueue(
                        session,
                        candidate.getRole(),
                        active -> {
                            context.setLeader(active);

                            if (active) {
                                try {
                                    candidate.onGranted(context);
                                    leaderEventPublisher.publishOnGranted(SolaceLeaderInitiator.this, context, candidate.getRole());
                                } catch (InterruptedException e) {
                                    logger.error("Unable to tell candidate that leader was granted.");
                                }
                            } else {
                                candidate.onRevoked(context);
                                leaderEventPublisher.publishOnRevoked(SolaceLeaderInitiator.this, context, candidate.getRole());
                            }
                        }
                );
            } catch (ProvisioningException | JCSMPException e) {
                logger.error("Unable to bind queue \"" + candidate.getRole() + "\". Your have to create the queue manually", e);
                leaderEventPublisher.publishOnFailedToAcquire(SolaceLeaderInitiator.this, context, candidate.getRole());
            }

            try {
                elector.start();
            } catch (JCSMPException e) {
                logger.error("Unable to start the flow. Your will never be the leader.", e);
                leaderEventPublisher.publishOnFailedToAcquire(SolaceLeaderInitiator.this, context, candidate.getRole());
            }
        }

        public void destroy() {
            elector.close();
            elector = null;
            context = null;
        }

        public SolaceContext getContext() {
            return context;
        }
    }
}