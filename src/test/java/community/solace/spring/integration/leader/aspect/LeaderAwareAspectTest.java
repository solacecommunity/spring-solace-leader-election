package community.solace.spring.integration.leader.aspect;

import community.solace.spring.integration.leader.leader.SolaceLeaderInitiator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.integration.leader.Context;
import org.springframework.test.context.junit4.SpringRunner;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest
public class LeaderAwareAspectTest {

    private static final String ROLE_NAME = "roleOne";

    @MockBean()
    public SolaceLeaderInitiator solaceLeaderInitiator;

    @Autowired
    private TestBean testBean;

    @Before
    public void setUp() {
        this.testBean.reset();
    }

    @Test
    public void testLeaderAwareAnnotatedMethodIsInvokedWhenIsLeader() {
        // given
        Context context = mock(Context.class);
        when(context.isLeader()).thenReturn(true);
        when(solaceLeaderInitiator.getContext(anyString())).thenReturn(context);

        // when
        testBean.testMethod();

        // then
        Assert.assertTrue(testBean.isInvoked());
    }

    @Test
    public void testLeaderAwareAnnotatedMethodIsNotInvokedWhenNotLeader() {
        // given
        Context context = mock(Context.class);
        when(context.isLeader()).thenReturn(false);
        when(solaceLeaderInitiator.getContext(anyString())).thenReturn(context);

        // when
        testBean.testMethod();

        // then
        Assert.assertFalse(testBean.isInvoked());
    }

    static class TestBean {

        private boolean invoked = false;

        @LeaderAware(value = ROLE_NAME)
        void testMethod() {
            this.invoked = true;
        }

        void reset() {
            this.invoked = false;
        }

        boolean isInvoked() {
            return invoked;
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        public LeaderAwareAspect leaderAwareAspect() {
            return new LeaderAwareAspect();
        }

        @Bean
        public TestBean testBean() {
            return new TestBean();
        }

    }

}