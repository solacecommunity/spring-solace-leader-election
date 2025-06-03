package community.solace.spring.integration.leader.leader;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.event.DefaultLeaderEventPublisher;
import org.springframework.integration.leader.event.LeaderEventPublisher;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class LeaderEventDebouncer implements LeaderEventPublisher, AutoCloseable {
    private final LeaderEventPublisher leaderEventPublisher;
    private final ScheduledExecutorService scheduler;
    private final Duration delay;
    private final Map<String, ScheduledFuture<?>> pendingTasks = new HashMap<>();

    LeaderEventDebouncer(ApplicationEventPublisher applicationEventPublisher, Duration delay) {
        this.leaderEventPublisher = new DefaultLeaderEventPublisher(applicationEventPublisher);
        this.delay = delay;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void publishOnGranted(Object source, Context context, String role) {
        scheduleEvent(role, () ->
                leaderEventPublisher.publishOnGranted(source, context, role));
    }

    @Override
    public void publishOnRevoked(Object source, Context context, String role) {
        scheduleEvent(role, () ->
                leaderEventPublisher.publishOnRevoked(source, context, role));
    }

    @Override
    public void publishOnFailedToAcquire(Object source, Context context, String role) {
        scheduleEvent(role, () ->
                leaderEventPublisher.publishOnFailedToAcquire(source, context, role));
    }

    private void scheduleEvent(String role, Runnable task) {
        synchronized (pendingTasks) {
            ScheduledFuture<?> existing = pendingTasks.get(role);
            if (existing != null) {
                existing.cancel(false);
            }

            ScheduledFuture<?> future = scheduler.schedule(() -> {
                        try {
                            task.run();
                        } finally {
                            pendingTasks.remove(role);
                        }
                    },
                    delay.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            pendingTasks.put(role, future);
        }
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
