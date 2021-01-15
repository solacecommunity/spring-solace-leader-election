package com.solace.spring.integration.leader;

import com.solace.spring.integration.leader.aspect.LeaderAwareAspect;
import com.solace.spring.integration.leader.leader.SolaceLeaderInitiator;
import com.solacesystems.jcsmp.SpringJCSMPFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SolaceLeaderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SolaceLeaderInitiator solaceLeaderInitiator(SpringJCSMPFactory solaceFactory) {
        return new SolaceLeaderInitiator(solaceFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public LeaderAwareAspect leaderAwareAspect() {
        return new LeaderAwareAspect();
    }

}
