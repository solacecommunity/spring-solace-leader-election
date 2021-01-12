package com.solace.spring.integration.leader;

import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.SpringJCSMPFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

public class SolaceLeaderInstanceRegistrar implements SmartInitializingSingleton {

    private static final Log logger = LogFactory.getLog(SolaceLeaderInstanceRegistrar.class);

    /**
     * The bean name for the {@link SolaceLeaderInstanceRegistrar} instance.
     */
    public static final String BEAN_NAME = "solaceLeaderInstanceRegistrar";

    private SpringJCSMPFactory solaceFactory;
    private JCSMPSession session;

    public  SolaceLeaderInstanceRegistrar(SpringJCSMPFactory solaceFactory) {
        this.solaceFactory = solaceFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (this.session == null) {
            try {
                // TODO, connected check: no connected == not leader
                session = solaceFactory.createSession();
            } catch (InvalidPropertiesException e) {
                logger.warn("Unable to create JCSMPSession, please check configuration", e);
            }
        }

    }
}
