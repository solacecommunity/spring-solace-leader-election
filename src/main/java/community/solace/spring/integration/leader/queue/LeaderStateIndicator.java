package community.solace.spring.integration.leader.queue;

import com.solacesystems.jcsmp.JCSMPException;

public interface LeaderStateIndicator {

    void start(String candidateName) throws JCSMPException;

    void stop();

    boolean isActive();
}
