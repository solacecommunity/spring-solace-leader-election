package com.solace.spring.integration.leader.aspect;

import java.lang.reflect.Method;

import com.solace.spring.integration.leader.leader.SolaceLeaderInitiator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.integration.leader.Context;

@Aspect
public class LeaderAwareAspect implements ApplicationContextAware {

	private static final Log logger = LogFactory.getLog(LeaderAwareAspect.class);

	private ApplicationContext applicationContext;

	@Around("@annotation(com.solace.spring.integration.leader.aspect.LeaderAware)")
	public Object leaderAware(ProceedingJoinPoint joinPoint) throws Throwable {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Method method = signature.getMethod();
		String role = method.getAnnotation(LeaderAware.class).value();

		SolaceLeaderInitiator leaderInitiator = applicationContext.getBean(SolaceLeaderInitiator.class);

		Context context = leaderInitiator.getContext(role);
		if (context == null) {
			if (!leaderInitiator.hasJoinGroupsConfig(role)) {
				logger.warn("LeaderAware: " + joinPoint + " group: '" + role + "' not jet joined and no configuration found!");
			}
			return null;
		}

		if (context.isLeader()) {
			return joinPoint.proceed();
		}

		logger.debug("LeaderAware: " + joinPoint + " group: '" + role + "' is not the leader");
		return null;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}