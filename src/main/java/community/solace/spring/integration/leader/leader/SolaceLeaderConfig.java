package community.solace.spring.integration.leader.leader;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

	private List<JoinGroupConfig> joinGroups;

	/**
	 * false: All groups going to be joined PROGRAMMATIC, have to be defined in application.[ini|yaml]
	 * true: All groups that will be joined PROGRAMMATIC, have not to be defined.
	 */
	private boolean permitAnonymousGroups = false;

	public Map<String, LEADER_GROUP_JOIN> getJoinGroups() {
		return (joinGroups == null) ? Collections.emptyMap() : joinGroups.stream()
				.collect(Collectors.toMap(
						JoinGroupConfig::getGroupName,
						JoinGroupConfig::getJoinType
				));
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
