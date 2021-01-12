package com.solace.spring.integration.leader.config;

import com.solace.spring.integration.leader.SolaceLeaderInstanceRegistrar;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.integration.config.IntegrationConfigurationInitializer;

/**
 * The Solace Integration infrastructure {@code beanFactory} initializer.
 *
 * Is referenced by {@link /src/main/resources/META-INF/spring.factories}
 */
public class SolaceLeaderAutoConfiguration implements IntegrationConfigurationInitializer {
    @Override
    public void initialize(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        BeanDefinitionRegistry beanDefinitionRegistry = (BeanDefinitionRegistry) beanFactory;
        if (!beanDefinitionRegistry.containsBeanDefinition(SolaceLeaderInstanceRegistrar.BEAN_NAME)) {
            beanDefinitionRegistry.registerBeanDefinition(SolaceLeaderInstanceRegistrar.BEAN_NAME,
                    new RootBeanDefinition(SolaceLeaderInstanceRegistrar.class));
        }
    }
}
