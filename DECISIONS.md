# Decisions

The major decisions behind `appointment-service` and `notification-service`. Each one lists what was decided, an alternative that was
rejected, and what would change my mind. Unhandled assumptions are at the end.

## 1. PostgreSQL for both services

- **Decision:** PostgreSQL, with one separate database per service.
- **Rejected:** H2 (easy to run, but it lacks the exclusion constraint, `FOR UPDATE SKIP LOCKED` and the same concurrency behaviour, so
  the tests would prove nothing about production); MongoDB (no equivalent of a database-enforced "no overlapping bookings" rule).
- **Change my mind if:** the booking guarantee moved out of the database (for example into a dedicated scheduling engine), or
  the data became document-shaped with no relational integrity needs.
- **Related:** the tests start a throw-away embedded PostgreSQL (no Docker needed) so they exercise the real constraints.

## 2. Double-booking is prevented by a database exclusion constraint

- **Decision:** `EXCLUDE USING gist (doctor_id WITH =, tsrange(start, start + 30 min) WITH &&) WHERE status = 'BOOKED'`. The service flushes
  the insert and turns the violation into `409`. Cancelled rows are excluded, so a cancelled slot can be re-booked.
- **Rejected:** (a) check-then-insert in code or `SELECT ... FOR UPDATE` (race-prone across instances, needs locking discipline);
  (b) a partial unique index on `(doctor_id, start_time)` - this was the original version (`V2`), replaced in `V5`, because it only stops
  *identical* start times, not overlapping windows if the slot length or alignment ever changes.
- **Change my mind if:** appointment length became variable per doctor/visit type and overlap rules became more complex than one range check.

## 3. Transactional outbox for publishing events

- **Decision:** the booking and an `outbox_events` row are written in one transaction; a scheduled publisher (every 2 s) sends `PENDING`
  rows to Kafka oldest first and marks them `PUBLISHED`. Delivery is at-least-once.
- **Rejected:** publishing straight to Kafka inside the request (booking would fail or hang whenever Kafka is down, which the brief rules out);
  publishing after commit with no stored record (event is lost if the app dies in between); CDC (Debezium) - correct but heavy for this size.
- **Change my mind if:** event volume made polling a bottleneck (move to CDC), or Kafka became reliably available and latency mattered more than simplicity.

## 4. What happens when Kafka is down

- **Decision:** booking still returns `201` and the receptionist sees nothing unusual. The event waits as `PENDING` and the notification is
  **delayed, not lost**; it is sent once Kafka returns. Producer timeouts are a few seconds, and only the background publisher talks to Kafka.
- **Rejected:** returning an error or a warning to the receptionist (the booking itself succeeded and is correct); retrying forever in the request thread.
- **Change my mind if:** notifications became time-critical (for example same-day reminders), in which case I would surface "notification delayed"
  to the user and alert on the age of the oldest `PENDING` row.

## 5. Duplicate events: idempotent consumer keyed by `eventId`

- **Decision:** the notification is stored first with a UNIQUE `event_id`. A repeated event that was already `SENT` is ignored; one that is
  stored but unsent only repeats the send; a racing duplicate hits the unique constraint and reuses the stored row.
- **Rejected:** relying on Kafka exactly-once semantics (does not cover the outbox-to-Kafka step or a crash between send and offset commit);
  de-duplicating in memory (lost on restart, wrong with several instances).
- **Change my mind if:** the real sender could not tolerate a repeat at all (an SMS may be sent but the app crash before `SENT` is saved);
  then I would need an idempotency key at the provider.

## 6. Retries and dead-letter: Kafka error handler with back-off

- **Decision:** the stub sender fails ~30% of the time. A failure is recorded, then the listener retries with back-off 1 s, 2 s, 4 s (3 retries),
  then publishes to `appointment.booked.dlt` and marks the notification `DEAD_LETTERED`. Unreadable events skip retries and go straight to the
  dead-letter topic. The offset is committed only after success or dead-lettering.
- **Rejected:** unlimited retries (violates "do not retry forever" and blocks the partition); separate retry topics with delays (non-blocking, but
  far more moving parts for this scope); swallowing the error and marking `FAILED` (loses the event).
- **Change my mind if:** volume grew enough that blocking a partition during back-off hurt throughput; then I would use non-blocking retry topics.

## 7. The two services share no code; the event is the contract

- **Decision:** each service has its own copy of the event record and its own database. The consumer ignores unknown fields. The event carries
  ids only (no names, email or phone), and the notification text is built from the ids.
- **Rejected:** a shared library for the event (couples releases); putting patient details in the event (spreads personal data into Kafka and
  the dead-letter topic); the notification service calling the appointment service for details (adds a synchronous dependency in the async path).
- **Change my mind if:** real messages needed the patient's name or contact details; then I would add a read model or a lookup call with its own failure handling.

## 8. Mock authentication with a header, roles enforced on the server

- **Decision:** the `X-Role: RECEPTIONIST | ADMIN` header is turned into a Spring Security role; URL rules and the service layer enforce permissions.
  Receptionists get patients, doctors (read) and appointments; everything else is ADMIN only. Cancelling an appointment that starts less than 24 hours
  away is ADMIN only (exactly 24 hours away is still allowed).
- **Rejected:** a locally signed JWT (more realistic, but more code and key handling than the brief needs); trusting roles only in the caller.
- **Change my mind if:** this left a development environment: any real deployment needs signed tokens from an identity provider, because the header can be forged by anyone.

## 9. Time handling: one clinic time zone, local date-times

- **Decision:** one configured zone (`Asia/Kolkata`); start times are local date-times without offsets, on 30-minute boundaries and in the future.
  An injectable `Clock` makes "now" controllable.
- **Rejected:** storing UTC instants (correct for multi-zone systems, but the clinic has a single zone and clients would have to convert).
- **Change my mind if:** the clinic moved to a zone with daylight saving or added a second location.

## 10. Notification send happens outside any database transaction

- **Decision:** store the notification (committed), then send, then record the result in a separate small transaction.
- **Rejected:** one big transaction around the slow, failure-prone send (holds a connection open and rolls back the stored record on failure,
  losing the attempt history).
- **Change my mind if:** never for this reason; it is a standing rule for any external call.

## 11. Tests: unit tests per package plus a separate integration folder

- **Decision:** fast Mockito unit tests mirror the main packages; integration tests live in `integration/` and run against an embedded PostgreSQL.
  `./gradlew test` runs everything. Required cases (parallel booking, duplicate event, re-book after cancel, receptionist rejected) are covered.
- **Rejected:** Testcontainers (needs Docker on every machine); H2 (see decision 1).
- **Change my mind if:** CI always had Docker and I wanted to test against the exact production image and version.

## Assumptions made but not handled (out of time)

- **No cancellation event.** Only bookings notify the patient; cancelling does not produce a message.
- **No reschedule, working hours, holidays or doctor leave.** Any future 30-minute boundary is bookable.
- **A patient can hold overlapping appointments with different doctors.** Only the doctor is protected from double-booking.
- **No duplicate-patient detection** (same name and phone can be registered twice) and no uniqueness on email.
- **`GET /appointments` is not paginated**; patients are.
- **Notification endpoint** is only protected by Spring Security's default login (local credentials set in `docker-compose.yml`); it is a development
  inspection tool.
- **Dead-lettered events need manual replay;** there is no tool for it, and old `PUBLISHED` outbox rows are never cleaned up.
- **The stub sender is the only "channel";** there is no real email/SMS, consent or rate limiting.
- **Ordering** is guaranteed per appointment only (the Kafka key is the appointment id), not across appointments.
- **The 24-hour rule uses the clinic clock at the time of the call;** there is no grace period or override reason recorded.
