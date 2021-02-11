package com.solace.spring.integration.leader.leader;


import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.solace.spring.integration.leader.leader.SolaceLeaderConfig.LEADER_GROUP_JOIN;
import com.solace.spring.integration.leader.queue.ProvisioningException;
import com.solace.spring.integration.leader.queue.SolaceLeaderViaQueue;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.SpringJCSMPFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.DefaultCandidate;
import org.springframework.integration.leader.event.DefaultLeaderEventPublisher;
import org.springframework.integration.leader.event.LeaderEventPublisher;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * Bootstrap leadership {@link org.springframework.integration.leader.Candidate candidates}
 * with Solace.
 * <p>
 * Mention, that your queue failover timeout is configured at:
 * (configure/client-profile/service)# min-keepalive-timeout 10
 * on your broker.
 */
@ManagedResource()
public class SolaceLeaderInitiator implements ApplicationEventPublisherAware, ApplicationListener<ApplicationReadyEvent> {

	private static final Log logger = LogFactory.getLog(SolaceLeaderInitiator.class);
	private static final String SOLACE_GROUP_PREFIX = "leader.";

	/**
	 * Leader event publisher.
	 */
	private volatile LeaderEventPublisher leaderEventPublisher = new DefaultLeaderEventPublisher();
	private final JCSMPSession session;
	private final Map<String, LeaderGroupContainer> leaderGroups = new HashMap<>();
	private final SolaceLeaderConfig leaderConfig;
	private final ApplicationContext appContext;

	public SolaceLeaderInitiator(SpringJCSMPFactory solaceFactory, SolaceLeaderConfig solaceLeaderConfig, ApplicationContext appContext) {
		this.leaderConfig = solaceLeaderConfig;
		try {
			this.session = solaceFactory.createSession();
		}
		catch (InvalidPropertiesException e) {
			throw new IllegalArgumentException("Missing solace broker configuration, for leader election", e);
		}
		this.appContext = appContext;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.leaderEventPublisher = new DefaultLeaderEventPublisher(applicationEventPublisher);
	}

	@ManagedOperation(description = "Join a leader group")
	public void joinGroup(String role) {
		joinGroup(new DefaultCandidate(UUID.randomUUID().toString(), role));
	}

	@SuppressWarnings("unused")
	public void joinGroup(Candidate candidate) {
		if (leaderGroups.containsKey(candidate.getRole())) {
			throw new IllegalArgumentException("A candidate with role \"" + candidate
					.getRole() + "\" was already registered");
		}

		if (!leaderConfig.getJoinGroups().containsKey(candidate.getRole()) && !leaderConfig.isPermitAnonymousGroups()) {
			throw new IllegalArgumentException("The role \"" + candidate
					.getRole() + "\" is not defined in your configuration at: spring.leader.join-groups. And spring.leader.permit-anonymous-groups = false.");
		}

		LeaderGroupContainer container = new LeaderGroupContainer(candidate);
		leaderGroups.put(candidate.getRole(), container);
	}

	public Context getContext(final String role) {
		LEADER_GROUP_JOIN groupJoinType = leaderConfig
				.getJoinGroups()
				.getOrDefault(role, LEADER_GROUP_JOIN.PROGRAMMATIC);
		boolean autoJoin = LEADER_GROUP_JOIN.FIRST_USE.equals(groupJoinType);

		return getContext(role, autoJoin);
	}

	public Context getContext(final String role, final boolean autoJoin) {
		LeaderGroupContainer leaderGroup = leaderGroups.get(role);
		if (leaderGroup == null) {
			if (autoJoin) {
				joinGroup(new DefaultCandidate(UUID.randomUUID().toString(), role));
				leaderGroup = leaderGroups.get(role);
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

		for (String definedRole : this.leaderConfig.getJoinGroups().keySet()) {
			status.putIfAbsent(definedRole, "not joined");
		}

		// Convert to pretty printed table.
		int keyColumnWidth = status.keySet().stream().mapToInt(String::length).max().getAsInt() + 2;
		return status.entrySet().stream()
				.map(eS -> String.format("%1$-" + keyColumnWidth + "s", eS.getKey() + ": ") + eS.getValue())
				.collect(Collectors.toList());
	}

	@ManagedOperation(description = "yield the leadership of the given group")
	public void yieldLeaderShip(String role) {
		getContext(role).yield();
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		if (!Objects.equals(appContext, event.getApplicationContext())) {
			// Workaround for: https://github.com/spring-cloud/spring-cloud-stream/issues/2083
			return;
		}
		for (Map.Entry<String, LEADER_GROUP_JOIN> groupToJoin : leaderConfig.getJoinGroups().entrySet()) {
			if (LEADER_GROUP_JOIN.ON_READINESS.equals(groupToJoin.getValue()) &&
					!leaderGroups.containsKey(groupToJoin.getKey())) {
				joinGroup(groupToJoin.getKey());
			}
		}
	}

	public boolean hasJoinGroupsConfig(String role) {
		return leaderConfig.getJoinGroups().containsKey(role);
	}

	private class LeaderGroupContainer {
		private SolaceContext context;
		private SolaceLeaderViaQueue elector;

		private LeaderGroupContainer(Candidate candidate) {

			context = new SolaceContext(candidate, () -> {
				try {
					elector.stop();
					context.setLeader(false);
					candidate.onRevoked(context);
					leaderEventPublisher.publishOnRevoked(SolaceLeaderInitiator.this, context, candidate.getRole());

					elector.start();
				}
				catch (JCSMPException e) {
					logger.error("yield failed: unable to start the flow. Your will never be the leader.", e);
					leaderEventPublisher
							.publishOnFailedToAcquire(SolaceLeaderInitiator.this, context, candidate.getRole());
				}
			});

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
						}
				);
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