package community.solace.spring.integration.leader.leader;

public class JoinGroupConfig {
	private String groupName;
	private SolaceLeaderConfig.LEADER_GROUP_JOIN joinType;

	String getGroupName() {
		return groupName;
	}

	void setGroupName(String groupName) {
		this.groupName = groupName;
	}

	SolaceLeaderConfig.LEADER_GROUP_JOIN getJoinType() {
		return joinType;
	}

	void setJoinType(SolaceLeaderConfig.LEADER_GROUP_JOIN joinType) {
		this.joinType = joinType;
	}
}