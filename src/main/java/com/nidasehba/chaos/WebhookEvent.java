package com.nidasehba.chaos;

/**
 * A status notification from the gateway. {@code sequence} increases with every status change of a
 * charge (1 = pending, 2 = final), so a receiver can tell an old event from a new one even when they
 * arrive out of order.
 */
public record WebhookEvent(String eventId, String chargeId, String idempotencyKey, int sequence, ChargeStatus status) {
}
