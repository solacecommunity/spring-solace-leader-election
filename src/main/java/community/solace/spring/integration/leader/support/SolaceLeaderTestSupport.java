package community.solace.spring.integration.leader.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Test support utility for simulating leader election in Solace.
 * Provides an API for developers writing tests to dynamically change leadership
 * and define initial leaders.
 */
public class SolaceLeaderTestSupport {

    private static final Log logger = LogFactory.getLog(SolaceLeaderTestSupport.class);

    private final Map<String, Consumer<Boolean>> handlers = new ConcurrentHashMap<>();
    private final List<String> initialLeaders = new CopyOnWriteArrayList<>();

    /**
     * Dynamically change leadership for a specific group.
     *
     * @param groupName The name of the leader group.
     * @param isLeader  True to grant leadership, false to revoke leadership.
     */
    public void setLeadership(String groupName, boolean isLeader) {
        Consumer<Boolean> handler = handlers.get(groupName);
        if (handler != null) {
            logger.info(String.format("Setting leadership for group '%s' to %s", groupName, isLeader));
            handler.accept(isLeader);
        } else {
            throw new IllegalStateException(String.format("No handler registered for group '%s'. Leadership cannot be set.", groupName));
        }
    }

    /**
     * Set the initial leaders that should automatically become leaders when they join.
     *
     * @param groupNames List of group names to be initial leaders.
     */
    public void setInitialLeaders(List<String> groupNames) {
        this.initialLeaders.clear();
        if (groupNames != null) {
            this.initialLeaders.addAll(groupNames);
        }
    }

    /**
     * Internal method to register a handler for a group.
     *
     * @param groupName    the group name.
     * @param eventHandler the event handler to register.
     */
    void registerHandler(String groupName, Consumer<Boolean> eventHandler) {
        handlers.put(groupName, eventHandler);
    }

    /**
     * Internal method to check if a group should be an initial leader.
     *
     * @param groupName the group name.
     * @return true if it should be an initial leader.
     */
    boolean isInitialLeader(String groupName) {
        return initialLeaders.contains(groupName);
    }
}
