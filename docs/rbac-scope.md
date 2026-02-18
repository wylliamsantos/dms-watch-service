# RBAC scope for `dms-watch-service`

## Current scope (Go-Live Alpha)

`dms-watch-service` does **not** expose business HTTP controllers/endpoints in the current architecture.

- HTTP is limited to operational endpoints (e.g. actuator/health)
- Business flow is asynchronous (`watch folder` -> `RabbitMQ` -> downstream consumers)

Because there is no business HTTP API surface today, the RBAC matrix
`owner/admin/reviewer/viewer` is **out of scope for API authorization** in this service.

## Security boundary applied here

For Alpha, the enforcement boundary is:

1. trusted producer runtime (watch folders configured per tenant)
2. message contract carrying `tenantId`
3. downstream services (`audit/search/document`) enforcing tenant isolation and RBAC on their APIs

## Revisit trigger

This decision must be revisited when `dms-watch-service` introduces any business HTTP endpoint.
At that point, apply the same minimal production RBAC policy used in `document/search/audit`.
