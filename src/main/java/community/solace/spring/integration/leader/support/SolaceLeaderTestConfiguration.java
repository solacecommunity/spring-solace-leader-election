package community.solace.spring.integration.leader.support;

import community.solace.spring.integration.leader.leader.SolaceLeaderConfig;
import community.solace.spring.integration.leader.queue.LeaderStateIndicatorProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration for Solace Leader Election.
 * Provides a test {@link LeaderStateIndicatorProvider} to allow simulating leadership
 * without a real Solace broker or any mocked JCSMP classes.
 */
@TestConfiguration
@EnableConfigurationProperties(SolaceLeaderConfig.class)
public class SolaceLeaderTestConfiguration {

    @Bean
    public SolaceLeaderTestSupport solaceLeaderTestSupport() {
        return new SolaceLeaderTestSupport();
    }

    @Bean
    @Primary
    public LeaderStateIndicatorProvider testLeaderStateIndicatorProvider(SolaceLeaderTestSupport testSupport) {
        return (roleName, eventHandler, onError) -> new TestLeaderStateIndicator(roleName, eventHandler, testSupport);
    }
}
