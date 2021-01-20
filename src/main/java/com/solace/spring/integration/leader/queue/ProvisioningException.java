package com.solace.spring.integration.leader.queue;


import org.springframework.core.NestedRuntimeException;

@SuppressWarnings("serial")
public class ProvisioningException extends NestedRuntimeException {

    /**
     * Constructor that takes a message and a root cause.
     * @param msg the detail message
     * @param cause the cause of the exception. This argument is generally expected to be
     * middleware specific.
     */
    public ProvisioningException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
