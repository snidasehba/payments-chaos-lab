# Payments Chaos Lab

Hackathon-style weekend build. In payments, the bugs that cost money don't show up on the happy path.
They show up when a response is lost after the card was already charged, when a webhook arrives twice,
or when "succeeded" arrives before "pending". This project is a small lab for testing exactly that:
a **fake payment gateway that misbehaves on purpose**, two payment clients, and a **seeded chaos test
suite** that checks money invariants after hundreds of random fault mixes.

Built with **Java 17** and **JUnit 5**, no other dependencies. Runs in **GitHub Actions**.

## What the gateway can do to you

| Fault | What happens | Why it is dangerous |
|---|---|---|
| Timeout before processing | Nothing is charged, the client gets no answer | The client must not mark the order paid |
| **Timeout after processing** | The card **is** charged, the response is lost | A retry with a new key charges the customer twice |
| 5xx | Request rejected before processing | Must be retried safely |
| Decline | A real business answer | Must not be retried into a success |
| Duplicate webhooks | Same event delivered again | Must not be applied twice |
| Shuffled webhooks | "succeeded" can arrive before "pending" | An old event must not knock a paid order back |

Every fault is driven by a seed, so any failing run can be replayed exactly.

## The invariants (checked after every chaos run)

1. **No double charge**: at most one successful charge per order.
2. **Nothing left in limbo**: after webhooks and reconciliation, no order is still `PENDING`.
3. **Status matches reality**: an order is paid if and only if the customer was actually charged.
4. **Money reconciles**: what the merchant thinks it collected equals what the gateway collected.

## Two clients, one suite

- `ResilientPaymentClient`: one idempotency key per order reused on every retry, retries only on
  timeouts and 5xx, webhooks deduplicated by event id and applied only if their sequence is newer,
  and reconciliation against the gateway for anything still pending.
- `NaivePaymentClient`: the classic mistakes (a new key per retry, webhooks applied blindly, no
  reconciliation). It is kept on purpose: **a chaos suite is only worth running if it catches bugs**,
  and a test asserts that it does.

```
$ java -jar target/payments-chaos-lab.jar
resilient client: 0 of 500 chaos runs broke an invariant
naive     client: 476 of 500 chaos runs broke an invariant
          first failing seed 1: double charge: order 14 has 2 successful charges
```

## Run it

```bash
mvn package                                  # unit tests + 400-seed chaos suite
java -jar target/payments-chaos-lab.jar 1000 25   # 1000 seeds, 25 orders per run
```

## Tests

11 JUnit tests: one per failure mode (lost response, never reached the gateway, 5xx, decline,
out-of-order and duplicate webhooks, key reuse with a different amount), the 400-seed chaos run
for the resilient client, seed replay, and the check that the chaos suite catches the naive client's
double charges and stuck orders.
