package com.solace.spring.integration.leader.queue;


import org.springframework.core.NestedRuntimeException;

/**
 * Generic unchecked exception to wrap middleware or technology specific exceptions.
 * Wrapped exceptions could be either checked or unchecked.
 *
 * See {@link NestedRuntimeException} for more usage details.
 *
 * @author Soby Chacko
 */
@SuppressWarnings("serial")
public class ProvisioningException extends NestedRuntimeException {

    /**
     * Constructor that takes a message.
     * @param msg the detail message
     */
    public ProvisioningException(String msg) {
        super(msg);
    }

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
