package com.nidasehba.chaos;

/** A charge as the gateway sees it. One idempotency key can only ever create one charge. */
public record Charge(String id, String idempotencyKey, long amountMinor, ChargeStatus status) {
}
