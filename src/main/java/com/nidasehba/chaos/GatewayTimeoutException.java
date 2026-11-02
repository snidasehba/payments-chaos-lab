package com.nidasehba.chaos;

/** The client gave up waiting. The charge may or may not have been created: that is the whole problem. */
public class GatewayTimeoutException extends RuntimeException {
    public GatewayTimeoutException(String message) {
        super(message);
    }
}
