package community.solace.spring.integration.leader.support;

import community.solace.spring.integration.leader.leader.SolaceLeaderInitiator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.integration.leader.Context;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(initiator.getContext(group).isLeader(), "Group should be leader");

        leaderAwareService.executeReadinessGroup();
        assertTrue(leaderAwareService.isReadinessGroupExecuted(), "Service should be executed when leadership is true");

        // set leadership to false
        leaderSupport.setLeadership(group, false);
        assertFalse(initiator.getContext(group).isLeader(), "Group should NOT be leader");

        leaderAwareService.reset();
        leaderAwareService.executeReadinessGroup();
        assertFalse(leaderAwareService.isReadinessGroupExecuted(), "Service should NOT be executed when leadership is false");
    }

    @Test
    public void testLeaderAwareTestGroupNotLeader() {
        String group = "test-group";
        initiator.joinGroup(group, true);
        leaderAwareService.reset();

        // leadership NOT set to true (default is false)
        assertFalse(initiator.getContext(group).isLeader(), "Group should NOT be leader initially");

        leaderAwareService.executeTestGroup();
        assertFalse(leaderAwareService.isTestGroupExecuted(), "Service should NOT be executed when leadership was not set to true");

        // Now set leadership to true and verify it works
        leaderSupport.setLeadership(group, true);
        leaderAwareService.executeTestGroup();
        assertTrue(leaderAwareService.isTestGroupExecuted(), "Service should be executed when leadership is set to true");
    }

    @Test
    public void testManualLeadershipSwitch() {
        String group = "programmatic-group-b";
        initiator.joinGroup(group, true);

        Context context = initiator.getContext(group);
        assertNotNull(context);
        assertFalse(context.isLeader(), "Should not be leader initially");

        leaderSupport.setLeadership(group, true);
        assertTrue(context.isLeader(), "Should be leader after setLeadership(true)");

        leaderSupport.setLeadership(group, false);
        assertFalse(context.isLeader(), "Should not be leader after setLeadership(false)");
    }

    @Test
    public void testInitialLeaders() {
        String group = "initial-group";
        leaderSupport.setInitialLeaders(Collections.singletonList(group));

        // We need to allow anonymous groups or define it in properties
        // For simplicity in test, let's just join it.
        initiator.joinGroup(group);

        Context context = initiator.getContext(group);
        assertNotNull(context);
        assertTrue(context.isLeader(), "Should be leader immediately due to setInitialLeaders");
    }

    @Test
    public void testOnReadiness() {
        String group = "readiness-group";
        // Given readiness-group is ON_READINESS in properties

        // During @SpringBootTest initialization, ApplicationReadyEvent is fired
        // SolaceLeaderInitiator.onApplicationReadyEvent will call joinGroup(group)
        // Since we didn't set it as initial leader, it should NOT be leader initially.

        Context context = initiator.getContext(group);
        assertNotNull(context);
        assertFalse(context.isLeader(), "Should not be leader yet");

        // Now manually set leadership
        leaderSupport.setLeadership(group, true);
        assertTrue(context.isLeader(), "Should be leader now");
    }
}
