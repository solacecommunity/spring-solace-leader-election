package community.solace.spring.integration.leader.support;

import community.solace.spring.integration.leader.queue.LeaderStateIndicator;

import java.util.function.Consumer;

/**
 * A test implementation of {@link LeaderStateIndicator} that integrates with
 * {@link SolaceLeaderTestSupport} to simulate leadership without a real Solace broker.
 */
class TestLeaderStateIndicator implements LeaderStateIndicator {

    private final String roleName;
    private final Consumer<Boolean> eventHandler;
    private final SolaceLeaderTestSupport testSupport;
    private boolean active;

    TestLeaderStateIndicator(String roleName, Consumer<Boolean> eventHandler, SolaceLeaderTestSupport testSupport) {
        this.roleName = roleName;
        this.eventHandler = eventHandler;
        this.testSupport = testSupport;
        this.active = false;

        testSupport.registerHandler(roleName, isLeader -> {
            this.active = isLeader;
            if (eventHandler != null) {
                eventHandler.accept(isLeader);
            }
        });
    }

    @Override
    public void start(String candidateName) {
        if (testSupport.isInitialLeader(roleName)) {
            this.active = true;
            if (eventHandler != null) {
                eventHandler.accept(true);
            }
        }
    }

    @Override
    public void stop() {
        this.active = false;
    }

    @Override
    public boolean isActive() {
        return active;
    }
}

