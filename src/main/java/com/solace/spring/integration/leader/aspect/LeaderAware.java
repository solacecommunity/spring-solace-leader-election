package com.solace.spring.integration.leader.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation in on a methode of a service, will disable the methode execution,
 * when this process is not the leader of the given group name.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LeaderAware {

    /**
     * The name of the leader group
     */
    String value();
}