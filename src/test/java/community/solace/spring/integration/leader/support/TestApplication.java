package community.solace.spring.integration.leader.support;
import community.solace.spring.integration.leader.aspect.LeaderAware;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;

@SpringBootApplication
public class TestApplication {
    @Service
    public static class LeaderAwareService {
        private boolean readinessGroupExecuted = false;
        private boolean testGroupExecuted = false;

        @LeaderAware("readiness-group")
        public void executeReadinessGroup() {
            this.readinessGroupExecuted = true;
        }

        @LeaderAware("test-group")
        public void executeTestGroup() {
            this.testGroupExecuted = true;
        }

        public void reset() {
            this.readinessGroupExecuted = false;
            this.testGroupExecuted = false;
        }

        public boolean isReadinessGroupExecuted() {
            return readinessGroupExecuted;
        }

        public boolean isTestGroupExecuted() {
            return testGroupExecuted;
        }
    }
}
