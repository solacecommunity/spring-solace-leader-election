package community.solace.spring.integration.leader.leader;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Health indicator for the solace leader
 */
public class SolaceLeaderHealthIndicator implements HealthIndicator {

    private Health.Builder health;



    @Override
    public Health health() {
        return health.build();
    }

    void down() {
        health = Health.down();
    }

    void down(Exception ex) {
        health = Health.down(ex);
    }

    void up() {
        health = Health.up();
    }

}
