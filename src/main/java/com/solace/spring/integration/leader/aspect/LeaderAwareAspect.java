package com.solace.spring.integration.leader.aspect;

import com.solace.spring.integration.leader.leader.SolaceLeaderInitiator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.integration.leader.Context;

import java.lang.reflect.Method;

@Aspect
public class LeaderAwareAspect implements ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(LeaderAwareAspect.class);

    private ApplicationContext applicationContext;

    @Pointcut("@annotation(com.solace.spring.integration.leader.aspect.LeaderAware)")
    public void leaderAwareAnnotationPointcut() {
    }

    @Around("leaderAwareAnnotationPointcut()")
    public Object leaderAware(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String role = method.getAnnotation(LeaderAware.class).role();

        SolaceLeaderInitiator solaceLeaderInitiator = applicationContext.getBean(SolaceLeaderInitiator.class);

        if (!solaceLeaderInitiator.isRunning()) {
            logger.warn("Advice: {} SolaceLeaderInitiator is not running ", joinPoint);
            return null;
        }

        Context context = solaceLeaderInitiator.getContext(role);
        if (context == null) {
            logger.warn("Advice: {} no context exists for role: '{}' ", joinPoint, role);
            return null;
        }
        
        if (context.isLeader()) {
            return joinPoint.proceed();
        }

        logger.debug("Advice: {} role: '{}' is not the leader", joinPoint, role);
        return null;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}