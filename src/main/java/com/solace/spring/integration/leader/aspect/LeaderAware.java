package com.solace.spring.integration.leader.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * if the {@link LeaderAware} annotation is present at a method declaration,  
 * the method is only executed if the specified {@link #value()} is a leader.
 * 
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LeaderAware {

    /**
     * Specifies the name of the leader group.
     */
    String value() default "";

    /**
     * Specifies the config variable containing name of the leader group.
     * Example:  process.name
     */
    String configValue() default "";
}