package com.nidasehba.chaos;

import java.util.Collection;

/** The system under test: something that takes payments through the gateway. */
public interface PaymentClient {

    Order pay(String orderId, long amountMinor);

    void onWebhook(WebhookEvent event);

    /** Settles orders that are still pending after webhooks, by asking the gateway directly. */
    void reconcile();

    Collection<Order> orders();
}
