package com.nidasehba.chaos;

/** A 5xx from the gateway before it processed the request. */
public class GatewayErrorException extends RuntimeException {
    public GatewayErrorException(String message) {
        super(message);
    }
}
