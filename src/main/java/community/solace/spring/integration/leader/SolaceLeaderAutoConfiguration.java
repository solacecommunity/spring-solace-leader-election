package community.solace.spring.integration.leader;

import java.util.UUID;

import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.SpringJCSMPFactory;
import community.solace.spring.integration.leader.aspect.LeaderAwareAspect;
import community.solace.spring.integration.leader.leader.SolaceLeaderConfig;
import community.solace.spring.integration.leader.leader.SolaceLeaderHealthIndicator;
import community.solace.spring.integration.leader.leader.SolaceLeaderInitiator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(SolaceLeaderConfig.class)
public class SolaceLeaderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SolaceLeaderInitiator solaceLeaderInitiator(JCSMPProperties jcsmpProperties, SolaceLeaderConfig solaceLeaderConfig, ApplicationContext appContext, SolaceLeaderHealthIndicator solaceLeaderHealthIndicator) {
        JCSMPProperties myJcsmpProperties = (JCSMPProperties) jcsmpProperties.clone();

        myJcsmpProperties.setProperty(JCSMPProperties.CLIENT_NAME, computeUniqueClientName(myJcsmpProperties));
        myJcsmpProperties.setProperty(JCSMPProperties.CLIENT_INFO_PROVIDER, new SolaceBinderClientInfoProvider());

        return new SolaceLeaderInitiator(new SpringJCSMPFactory(myJcsmpProperties), solaceLeaderConfig, appContext, solaceLeaderHealthIndicator);
    }

    @Bean
    @ConditionalOnMissingBean
    public SolaceLeaderHealthIndicator solaceLeaderSessionState() {
        return new SolaceLeaderHealthIndicator();
    }

    /**
     * Creates a unique client name to create a JCSMP session.
     * Otherwise, no connection to the broker can be established if the application also creates a JCSMP session,
     * because a unique client name is required for each session.
     */
    private String computeUniqueClientName(JCSMPProperties jcsmpProperties) {
        String clientName = (String) jcsmpProperties.getProperty(JCSMPProperties.CLIENT_NAME);
        if (!StringUtils.hasText(clientName)) {
            clientName = UUID.randomUUID().toString();
        }
        return clientName + ".solace-spring-integration-leader";
    }

    @Bean
    @ConditionalOnMissingBean
    public LeaderAwareAspect leaderAwareAspect() {
        return new LeaderAwareAspect();
    }

}
