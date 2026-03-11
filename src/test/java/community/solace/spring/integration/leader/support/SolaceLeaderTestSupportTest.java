package community.solace.spring.integration.leader.support;

import community.solace.spring.integration.leader.leader.SolaceLeaderInitiator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.integration.leader.Context;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
        "spring.leader.join-groups[0].group-name=programmatic-group-a",
        "spring.leader.join-groups[0].join-type=PROGRAMMATIC",

        "spring.leader.join-groups[1].group-name=programmatic-group-b",
        "spring.leader.join-groups[1].join-type=PROGRAMMATIC",

        "spring.leader.join-groups[2].group-name=readiness-group",
        "spring.leader.join-groups[2].join-type=ON_READINESS",

        "spring.leader.permit-anonymous-groups=true"
})
@Import(SolaceLeaderTestConfiguration.class)
public class SolaceLeaderTestSupportTest {

    @Autowired
    private SolaceLeaderTestSupport leaderSupport;

    @Autowired
    private SolaceLeaderInitiator initiator;

    @Autowired
    private TestApplication.LeaderAwareService leaderAwareService;

    @Test
    public void testLeaderAwareReadinessGroup() {
        String group = "readiness-group";
        leaderAwareService.reset();

        // set leadership to true
        leaderSupport.setLeadership(group, true);
        assertThat("Group should be leader", initiator.getContext(group).isLeader(), is(true));

        leaderAwareService.executeReadinessGroup();
        assertThat("Service should be executed when leadership is true", leaderAwareService.isReadinessGroupExecuted(), is(true));

        // set leadership to false
        leaderSupport.setLeadership(group, false);
        assertThat("Group should NOT be leader", initiator.getContext(group).isLeader(), is(false));

        leaderAwareService.reset();
        leaderAwareService.executeReadinessGroup();
        assertThat("Service should NOT be executed when leadership is false", leaderAwareService.isReadinessGroupExecuted(), is(false));
    }

    @Test
    public void testLeaderAwareTestGroupNotLeader() {
        String group = "test-group";
        initiator.joinGroup(group, true);
        leaderAwareService.reset();

        // leadership NOT set to true (default is false)
        assertThat("Group should NOT be leader initially", initiator.getContext(group).isLeader(), is(false));

        leaderAwareService.executeTestGroup();
        assertThat("Service should NOT be executed when leadership was not set to true", leaderAwareService.isTestGroupExecuted(), is(false));

        // Now set leadership to true and verify it works
        leaderSupport.setLeadership(group, true);
        leaderAwareService.executeTestGroup();
        assertThat("Service should be executed when leadership is set to true", leaderAwareService.isTestGroupExecuted(), is(true));
    }

    @Test
    public void testManualLeadershipSwitch() {
        String group = "programmatic-group-b";
        initiator.joinGroup(group, true);

        Context context = initiator.getContext(group);
        assertThat(context, notNullValue());
        assertThat("Should not be leader initially", context.isLeader(), is(false));

        leaderSupport.setLeadership(group, true);
        assertThat("Should be leader after setLeadership(true)", context.isLeader(), is(true));

        leaderSupport.setLeadership(group, false);
        assertThat("Should not be leader after setLeadership(false)", context.isLeader(), is(false));
    }

    @Test
    public void testInitialLeaders() {
        String group = "initial-group";
        leaderSupport.setInitialLeaders(Collections.singletonList(group));

        // We need to allow anonymous groups or define it in properties
        // For simplicity in test, let's just join it.
        initiator.joinGroup(group);

        Context context = initiator.getContext(group);
        assertThat(context, notNullValue());
        assertThat("Should be leader immediately due to setInitialLeaders", context.isLeader(), is(true));
    }

    @Test
    public void testOnReadiness() {
        String group = "readiness-group";
        // Given readiness-group is ON_READINESS in properties

        // During @SpringBootTest initialization, ApplicationReadyEvent is fired
        // SolaceLeaderInitiator.onApplicationReadyEvent will call joinGroup(group)
        // Since we didn't set it as initial leader, it should NOT be leader initially.

        Context context = initiator.getContext(group);
        assertThat(context, notNullValue());
        assertThat("Should not be leader yet", context.isLeader(), is(false));

        // Now manually set leadership
        leaderSupport.setLeadership(group, true);
        assertThat("Should be leader now", context.isLeader(), is(true));
    }
}
