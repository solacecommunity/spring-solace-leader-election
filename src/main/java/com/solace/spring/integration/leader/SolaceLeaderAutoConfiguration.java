package com.solace.spring.integration.leader;

import com.solace.spring.integration.leader.aspect.LeaderAwareAspect;
import com.solace.spring.integration.leader.leader.SolaceLeaderConfig;
import com.solace.spring.integration.leader.leader.SolaceLeaderInitiator;
import com.solacesystems.jcsmp.SpringJCSMPFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SolaceLeaderConfig.class)
public class SolaceLeaderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SolaceLeaderInitiator solaceLeaderInitiator(SpringJCSMPFactory solaceFactory, SolaceLeaderConfig solaceLeaderConfig) {
        return new SolaceLeaderInitiator(solaceFactory, solaceLeaderConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public LeaderAwareAspect leaderAwareAspect() {
        return new LeaderAwareAspect();
    }

}
