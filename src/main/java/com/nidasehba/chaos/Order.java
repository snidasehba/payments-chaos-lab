package com.nidasehba.chaos;

/** The merchant's view of one order. Mutable: the payment service moves it through its states. */
public class Order {

    private final String orderId;
    private final long amountMinor;
    private ChargeStatus status = ChargeStatus.PENDING;
    private String chargeId;
    private int lastSequence;

    public Order(String orderId, long amountMinor) {
        this.orderId = orderId;
        this.amountMinor = amountMinor;
    }

    public String orderId() {
        return orderId;
    }

    public long amountMinor() {
        return amountMinor;
    }

    public ChargeStatus status() {
        return status;
    }

    public String chargeId() {
        return chargeId;
    }

    int lastSequence() {
        return lastSequence;
    }

    void update(ChargeStatus status, String chargeId, int sequence) {
        this.status = status;
        this.chargeId = chargeId;
        this.lastSequence = sequence;
    }

    @Override
    public String toString() {
        return orderId + "=" + status + (chargeId == null ? "" : "(" + chargeId + ")");
    }
}
