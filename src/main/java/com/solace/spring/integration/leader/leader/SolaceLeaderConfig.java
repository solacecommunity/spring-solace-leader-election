package com.solace.spring.integration.leader.leader;

import java.util.Collections;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "spring.leader")
public class SolaceLeaderConfig {
	public enum LEADER_GROUP_JOIN {
		PROGRAMMATIC,     // You have to run, joinGroup
		FIRST_USE,    // getContext, will auto join group
		ON_READINESS  // Join after readiness event was fired.
	}

	private Map<String, LEADER_GROUP_JOIN> joinGroups;

	public Map<String, LEADER_GROUP_JOIN> getJoinGroups() {
		return (joinGroups == null) ? Collections.emptyMap() : joinGroups;
	}

	public void setJoinGroups(Map<String, LEADER_GROUP_JOIN> joinGroups) {
		this.joinGroups = joinGroups;
	}
}
