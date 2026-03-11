package community.solace.spring.integration.leader.queue;

import java.util.function.Consumer;

public interface LeaderStateIndicatorProvider {
    LeaderStateIndicator create(String roleName, Consumer<Boolean> eventHandler, Consumer<Throwable> onError);
}
