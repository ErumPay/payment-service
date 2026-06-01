# Core SSE Contract

This document fixes the Core payment SSE contract before multi-server migration.

## Endpoint

- `GET /api/v1/payment/{paymentId}/subscribe`

## Event Names

- `connected`: emitted once when subscription is established.
- `payment-updated`: emitted for every business update event.

## Data Shape

All SSE messages use the same payload model:

```json
{
  "eventType": "CONNECTED | PAYMENT_PENDING | PAYMENT_FAILED | PAYMENT_PAID | RECOMMENDATION_SUCCEEDED | RECOMMENDATION_FAILED",
  "paymentId": 123,
  "payload": {},
  "occurredAt": "2026-06-01T12:34:56"
}
```

## Event Type Semantics

- `CONNECTED`: subscription established.
- `PAYMENT_PENDING`: payment execution has started.
- `PAYMENT_FAILED`: payment execution failed.
- `PAYMENT_PAID`: payment execution succeeded (terminal).
- `RECOMMENDATION_SUCCEEDED`: recommendation result delivered.
- `RECOMMENDATION_FAILED`: recommendation request failed.

## Terminal Policy (Current)

- On `PAYMENT_PAID`, server completes SSE subscriptions for the payment.
- On `PAYMENT_FAILED`, server keeps connection open for follow-up updates.

## Replay Policy (Current)

- `connected` is sent first at subscribe.
- Recommendation events are cached in-memory for 10 minutes and replayed at subscribe when present.

## Internal Delivery Flow (Step 3)

- `publishPaymentUpdated(...)`: entrypoint for business code (`CoreService`, `CorePgPaymentService`), publishes to Redis channel.
- `CoreSseRedisSubscriber`: receives Redis messages on every instance.
- `applyPaymentUpdatedFromRedis(...)`: delegates local delivery.
- `sendLocalPaymentUpdated(...)`: sends the event only to SSE emitters connected on the current instance.
