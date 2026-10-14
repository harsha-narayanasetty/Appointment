# Appointment Service

REST API for patients, doctors and appointments. Publishes `AppointmentBooked` events to Kafka (via a transactional outbox).
See [architecture.md](architecture.md) for the design.

## Running everything with Docker

```
cp .env.example .env     # once; set your own passwords (.env is git-ignored)
docker compose up --build
```

Starts Kafka, a PostgreSQL per service, `appointment-service` (http://localhost:8080) and `notification-service`
(http://localhost:8081). The compose file builds the other service from `../notification`, so keep the two repos side by side.
Stop with `docker compose down` (add `-v` to also delete the data).

Try it (fictional data):

```
curl -X POST localhost:8080/patients -H 'X-Role: RECEPTIONIST' -H 'Content-Type: application/json' \
  -d '{"firstName":"Asha","lastName":"Menon","dateOfBirth":"1990-05-17","email":"asha@example.com","phone":"9876543210"}'
curl -X POST localhost:8080/appointments -H 'X-Role: RECEPTIONIST' -H 'Content-Type: application/json' \
  -d '{"patientId":1,"doctorId":201,"startTime":"2027-01-04T10:00:00"}'
curl localhost:8081/notifications
```

`GET /notifications` on `notification-service` is public and every other path is denied (development use only; do not expose it).
Database credentials come from `.env` (required: `APPOINTMENT_DB_USERNAME`, `APPOINTMENT_DB_PASSWORD`,
`NOTIFICATIONS_DB_USERNAME`, `NOTIFICATIONS_DB_PASSWORD`; see `.env.example`). Compose passes them to each service as
`DB_USERNAME` / `DB_PASSWORD`, and nothing secret is committed. To run a service outside Docker,
export `DB_USERNAME` and `DB_PASSWORD` before `./gradlew bootRun`.

## Test layout

- `src/test/java/.../<package>/` – fast unit tests, one test package per main package (JUnit 5, Mockito, given / when / then).
- `src/test/java/.../integration/` – integration tests against the real app and an embedded PostgreSQL: `AppointmentBookingTest`, `AuthorizationTest`, `SecurityRulesTest`, `RepositoryTest`.

## Running the tests

```
./gradlew test
```

Requires JDK 21 only. No Docker, Kafka or local PostgreSQL is needed: the tests start a temporary embedded PostgreSQL
(real Flyway migrations, real constraints) and point Kafka at an unused port, so they also cover "Kafka is down".

| Required test | Where |
|---|---|
| Many parallel bookings for the same doctor and time produce exactly one success | `AppointmentBookingTest` |
| A cancelled slot can be re-booked | `AppointmentBookingTest` |
| A RECEPTIONIST is rejected where only ADMIN is allowed | `AuthorizationTest` |
| The same event processed twice creates one notification | `NotificationIdempotencyTest` in the **Notifications** repo (`./gradlew test` there) |
