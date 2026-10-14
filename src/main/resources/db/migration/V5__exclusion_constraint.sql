CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointments ADD CONSTRAINT no_overlapping_bookings
  EXCLUDE USING gist (
      doctor_id WITH =,
      tsrange(start_time, start_time + interval '30 minutes') WITH &&
  ) WHERE (status = 'BOOKED');

DROP INDEX uq_doctor_slot_booked;