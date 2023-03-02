package community.solace.spring.integration.leader.leader;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
@ConfigurationProperties(prefix = "spring.leader")
public class SolaceLeaderConfig {
	public enum LEADER_GROUP_JOIN {
		PROGRAMMATIC,     // You have to run, joinGroup
		FIRST_USE,    // getContext, will auto join group
		ON_READINESS  // Join after readiness event was fired.
	}

	private List<JoinGroupConfig> joinGroups;

	/**
	 * false: All groups going to be joined PROGRAMMATIC, have to be defined in application.[ini|yaml]
	 * true: All groups that will be joined PROGRAMMATIC, have not to be defined.
	 */
	private boolean permitAnonymousGroups = false;

	public List<JoinGroupConfig> getJoinGroups() {
		return joinGroups;
	}

	public static Map<String, LEADER_GROUP_JOIN> getJoinGroupMap(SolaceLeaderConfig config) {
		if (CollectionUtils.isEmpty(config.getJoinGroups())) {
			return Collections.EMPTY_MAP;
		}

		Map<String, LEADER_GROUP_JOIN> joinGroup = new HashMap<>();
		for (JoinGroupConfig j : config.getJoinGroups()) {
			joinGroup.put(
					j.getGroupName(),
					j.getJoinType()
			);
		}
		return joinGroup;
	}

	public void setJoinGroups(List<JoinGroupConfig> joinGroups) {
		this.joinGroups = joinGroups;
	}

	public boolean isPermitAnonymousGroups() {
		return permitAnonymousGroups;
	}

	public void setPermitAnonymousGroups(boolean permitAnonymousGroups) {
		this.permitAnonymousGroups = permitAnonymousGroups;
	}


}
