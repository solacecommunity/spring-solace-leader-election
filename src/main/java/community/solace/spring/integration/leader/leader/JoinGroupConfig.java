package community.solace.spring.integration.leader.leader;

public class JoinGroupConfig {
	private String groupName;
	private SolaceLeaderConfig.LEADER_GROUP_JOIN joinType;

	private boolean yieldOnShutdown = true;

	public String getGroupName() {
		return groupName;
	}

	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}

	public SolaceLeaderConfig.LEADER_GROUP_JOIN getJoinType() {
		return joinType;
	}

	public void setJoinType(SolaceLeaderConfig.LEADER_GROUP_JOIN joinType) {
		this.joinType = joinType;
	}

	public boolean getYieldOnShutdown() {
		return yieldOnShutdown;
	}

	public void setYieldOnShutdown(boolean yieldOnShutdown) {
		this.yieldOnShutdown = yieldOnShutdown;
	}
}