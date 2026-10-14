# Clinic Appointment System – Architecture

The system is made of two independent services that talk to each other only through events.

| Service | Purpose |
|---|---|
| **Appointment Service** | Manages patients, doctors and appointments (book / view / cancel). |
| **Notification Service** | Reacts to newly booked appointments and notifies the patient. |

## 1. Big Picture

```mermaid
flowchart LR
    U["Clinic staff<br/>(Receptionist / Admin)"] -->|Book, view, cancel| A["Appointment Service"]
    A -->|Saves data| ADB[("Appointment DB")]
    A -->|"'Appointment Booked' event"| K{{"Message Broker<br/>topic: appointment.booked"}}
    K -->|Delivers event| N["Notification Service"]
    N -->|Saves result| NDB[("Notification DB")]
    N -->|Sends message| P["Patient<br/>(email / SMS – simulated)"]
    K -.->|"Events that could not be processed"| DLT{{"Dead-letter topic<br/>appointment.booked.dlt"}}
    N -.->|Gives up after retries| DLT
```

- The two services share **no code and no database**; the only contract is the *Appointment Booked* event.
- If the Notification Service is down, bookings still work and notifications are delivered later.

---

## 2. Appointment Service

### 2.1 Features

| Area | What it does |
|---|---|
| **Patients** | Register a patient; search patients by name (paged). |
| **Doctors** | List the available doctors (pre-loaded in the system). |
| **Appointments** | Book a slot with a doctor, list appointments (filter by doctor and/or status), cancel an appointment. |
| **Event publishing** | Every successful booking produces an *Appointment Booked* event for the Notification Service. |

### 2.2 Who can do what

| Action | Receptionist | Admin |
|---|:---:|:---:|
| Register / search patients | ✅ | ✅ |
| View doctors | ✅ | ✅ |
| Book / view appointments | ✅ | ✅ |
| Cancel appointment (more than 24h away) | ✅ | ✅ |
| Cancel appointment (within 24h) | ❌ | ✅ |
| Any other endpoint | ❌ | ✅ |

Callers identify their role with a header (mock authentication); a missing or unknown role is rejected as *Unauthorized*, and a role without permission is *Forbidden*.

### 2.3 Booking rules

- Appointments are **30 minutes** long and must start on a 30-minute mark (e.g. 10:00, 10:30).
- The start time must be **in the future** (clinic time zone: Asia/Kolkata).
- The patient and the doctor must exist.
- A doctor **cannot be double-booked**; overlapping slots are rejected (enforced by the database, so it holds even with simultaneous requests).
- Cancelled slots become available again.

### 2.4 Booking flow

```mermaid
sequenceDiagram
    actor R as Receptionist
    participant A as Appointment Service
    participant DB as Appointment DB

    R->>A: Book appointment (patient, doctor, time)
    A->>A: Check time is on a 30-min mark and in the future
    A->>DB: Verify patient and doctor exist
    A->>DB: Save appointment (status = BOOKED)
    alt Doctor already booked for that slot
        A-->>R: 409 Conflict
    else Slot is free
        A->>DB: Save "Appointment Booked" event in the outbox (same transaction)
        A-->>R: 201 Created
    end
```

### 2.5 Appointment lifecycle

```mermaid
stateDiagram-v2
    [*] --> BOOKED: Book
    BOOKED --> CANCELLED: Cancel<br/>(Receptionist: only if more than 24h away, Admin: any time)
    CANCELLED --> CANCELLED: Cancel again (no effect)
    CANCELLED --> [*]
```

### 2.6 Reliable event publishing (Outbox)

To make sure an event is never lost *and* never sent for an appointment that failed to save, the event is stored together with the appointment, then forwarded separately.

```mermaid
flowchart LR
    B["Book appointment"] --> T["One database transaction"]
    T --> S1["Appointment saved"]
    T --> S2["Event saved in Outbox<br/>(status: PENDING)"]
    S2 --> W["Publisher (checks every ~2 seconds)"]
    W -->|Send| K{{"Message Broker"}}
    K -->|Success| OK["Outbox row → PUBLISHED"]
    K -->|Broker down| RT["Stays PENDING and is retried on the next check"]
```

- Delivery is **at least once**: an event may occasionally be sent twice, which the Notification Service handles.
- A slow or unavailable broker never delays or fails a booking.

### 2.7 The event shared between services

| Field | Meaning |
|---|---|
| Event id | Unique id, used to detect duplicates |
| Event type | `AppointmentBooked` |
| Occurred at | When the booking happened |
| Appointment, Patient, Doctor ids | Who and what was booked |
| Start time | Appointment time (clinic local time) |

---

## 3. Notification Service

### 3.1 Features

| Area | What it does |
|---|---|
| **Listen** | Subscribes to the *Appointment Booked* topic. |
| **Store** | Saves one notification per event, with its status and number of attempts. |
| **Send** | Sends a message such as *"Appointment booked for patient P1 with doctor D2 at 2026-10-20T10:30."* (delivery is simulated and randomly fails ~30% of the time to exercise retries). |
| **Retry** | Retries failed sends with increasing waits (1s → 2s → 4s). |
| **Dead-letter** | After all retries fail, marks the notification `DEAD_LETTERED` and moves the event to a dead-letter topic so nothing is silently lost. |
| **No duplicates** | A repeated event is recognised by its id and is not sent twice. |
| **Inspect** | A read-only list of notifications, filterable by status (for development only, no authentication). |

### 3.2 Processing flow

```mermaid
flowchart TD
    E["Appointment Booked event received"] --> P{"Readable?"}
    P -- No --> DLT["Move to dead-letter topic"]
    P -- Yes --> D{"Already stored<br/>and SENT?"}
    D -- Yes --> I["Ignore duplicate"]
    D -- No --> ST["Store notification (PENDING)"]
    ST --> SEND["Send to patient"]
    SEND -->|Success| SENT["Mark SENT"]
    SEND -->|Failure| F["Record failure"]
    F --> R{"Retries left?<br/>(up to 3)"}
    R -- Yes --> W["Wait, then try again"] --> SEND
    R -- No --> DL["Mark DEAD_LETTERED<br/>and move to dead-letter topic"]
```

### 3.3 Notification lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING: Event received and stored
    PENDING --> SENT: Send succeeded
    PENDING --> PENDING: Send failed, retrying
    PENDING --> DEAD_LETTERED: Retries exhausted
    SENT --> [*]
    DEAD_LETTERED --> [*]
```

---

## 4. End-to-End Journey

```mermaid
sequenceDiagram
    actor R as Receptionist
    participant A as Appointment Service
    participant K as Message Broker
    participant N as Notification Service
    actor P as Patient

    R->>A: Book appointment
    A->>A: Save appointment + event (together)
    A-->>R: Booking confirmed
    A->>K: Publish "Appointment Booked" (shortly after)
    K->>N: Deliver event
    N->>N: Store notification
    N->>P: Send confirmation message
    N->>N: Mark as SENT
```

## 5. Guarantees at a Glance

| Guarantee | How it is achieved |
|---|---|
| No double-booking of a doctor | Database rule on doctor + time slot |
| No event without a booking, and no booking without an event | Outbox saved in the same transaction as the booking |
| Booking is not blocked by messaging problems | Events are sent in the background and retried |
| No notification sent twice | Unique event id checked by the Notification Service |
| No event silently lost | Retries, then dead-letter topic |
| Services can evolve independently | Separate code, separate databases, shared event only |
