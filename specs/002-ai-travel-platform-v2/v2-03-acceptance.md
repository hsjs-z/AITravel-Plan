# V2-03 Acceptance Evidence

**Date**: 2026-10-17

**Status**: PASS, awaiting user acceptance. V2-03 tasks remain unchecked.

## 1. Persistence and migration

- `travel_user_auth` existed before V2-03; no database creation was required.
- Flyway applied `V1__init.sql` successfully.
- Tables:
  - `app_user`
  - `flyway_schema_history`
- `flyway_schema_history`: `version=1`, `description=init`, `success=1`.

Verified rows after restart:

| email | role |
|---|---|
| `admin-v203@travel.local` | `ADMIN` |
| `v203chk@example.com` | `USER` |

Admin row count after repeated bootstrap: `1`.

## 2. Auth behavior

- Register returns token, userId and `role=USER`.
- Login returns the same userId and `role=USER`.
- `GET /users/me` returns `id/email/phone/role`.
- Duplicate email -> 409.
- Wrong password -> 401.
- Old tokens without `role` are treated as `USER` (gateway unit test).

## 3. Restart persistence

user-auth was stopped and restarted with the same admin environment variables.

- Login for `v203chk@example.com` succeeded after restart.
- userId remained `97643eaf-c299-4aac-a317-ca55fe104ab3`.
- Admin bootstrap did not create a duplicate row.
- Admin login after restart succeeded with `role=ADMIN`.

## 4. Gateway authorization and two distinct 404 paths

Real HTTP through gateway:

| Request | Status | Body |
|---|---:|---|
| USER token + `GET /admin/does-not-exist` | 404 | 0 bytes |
| ADMIN token + `GET /admin/does-not-exist` | 404 | 79 bytes unified body |
| USER token + forged `X-User-Role: ADMIN` | 404 | 0 bytes |
| Anonymous `GET /admin/does-not-exist` | 401 | 0 bytes |

ADMIN response body:

```json
{"code":40400,"message":"resource not found: admin/does-not-exist","data":null}
```

Inventory log contains the forwarded request:

```text
http GET /admin/does-not-exist traceId=09d6faeb7dc4803ca3e4fcf65f71e0cb
```

The USER request does not appear in the inventory log, proving it was blocked at the
gateway rather than forwarded.

## 5. Full main-chain regression

| Step | Result |
|---|---|
| Register/login | PASS, `USER` |
| Mock planning | `SUCCEEDED` |
| Draft itinerary | `DRAFT` |
| Confirm itinerary | `CONFIRMED` |
| One-click order | `BOOKED`, total 1020 |
| Pay | `PAID` |

Test identifiers:

- Planning task: `694c21d5-4851-445d-9260-9bba4a64c86e`
- Itinerary: `3a4bf2e4-4335-4ab7-a014-adb3f1853067`
- Order: `ef8e97a6-cf05-40d4-93f4-437858cfaa3f`

## 6. Build and tests

- `mvn -pl services/user-auth -am test`: common 5 + user-auth 5 tests passed.
- `mvn -pl gateway -am test`: common 5 + gateway 4 tests passed.
- `mvn clean install`: 10/10 modules `SUCCESS`.

## 7. Note on Outbox autoconfiguration

Adding JPA to user-auth activated `CommonOutboxAutoConfiguration`, whose class
signature references Kafka. user-auth has no Kafka dependency, so the auto-config
was given a class-level Kafka classpath condition. The seven event services continue
to load Outbox normally; the full build proves their tests still pass.
