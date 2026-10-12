# Amber

> Reminiscence and cognitive stimulation for people living with dementia, and peace of mind for the people who care for them.

**Figma:** [Design mockups](https://www.figma.com/design/wHW7M7HObl4S22BM02lFiK/View?node-id=0-1&t=pVwS4cfDWgUvTzio-1)

---

## App Description

Over 50 million people worldwide live with dementia, facing cognitive decline, short-term memory loss and severe isolation, while their families carry an intense psychological and financial burden. Dementia is irreversible, but structured social engagement can ease its symptoms and help preserve a person's sense of self. **Amber** is an AI-powered Android app built on two clinically recognised approaches, Cognitive Stimulation Therapy and Reminiscence Therapy. Patients revisit their life through interactive photo storytelling. Family members and other loved ones add photos and voice memos of shared memories.

Amber targets two user groups, represented by two fictional personas: patients in the early-to-moderate stages of dementia, like **Arthur**, who need a simple, frustration-free way to reminisce and stay mentally active, and caregivers (family members and other loved ones), like **Sarah**, who want a simple way to help their loved one through dementia by sharing photos and voice memos of past memories. Amber is meant for the whole family, not just one person assigned to the patient.

### Personas

*The personas below are fictional examples that represent our target users; they are not real people.*

| | Arthur: Patient persona | Sarah: Caregiver persona |
|---|---|---|
| **Age** | 78 | 45 |
| **Situation** | Early-to-moderate stage Alzheimer's | Family member of a patient |
| **Needs** | An easy, frustration-free way to reminisce and stay mentally active | A simple way to help her loved one through dementia by sharing photos and voice memos of past memories |
| **Uses Amber to** | Browse photo stories, tell and record memories by voice | Upload photos and record voice memos of shared memories |

---

## Split-App Model

Amber has **no self-hosted backend or custom server code**. It is a fully native Android client that talks directly to managed cloud services:

| Service | Role |
|---|---|
| **Firebase Authentication** | User identification, login flows and role management (Caregiver / Patient) |
| **Cloud Firestore** | Storage of user profiles, roles, family groups and engagement data, plus *metadata* about stories and media (description, author, `storagePath`, …) but not the files themselves, protected by Firestore Security Rules |
| **Firebase Storage** | Storage of photos and voice memos (Firestore only keeps their paths) |

> **AI implementation: to be decided.** The AI features (voice-to-text transcription and photo storytelling assistance) are part of the core design, but the model and how it will be integrated (managed cloud API vs. on-device model) have not been chosen yet. This section will be updated once the choice is made.

## Multi-User Support

- **Authentication:** Firebase Authentication provides secure sign-in.
- **Roles:** each account is either a **Caregiver** (family members and other loved ones included) or a **Patient**, with different permissions and interfaces.
- **Isolation:** Firestore and Firebase Storage Security Rules restrict each family's data to its own members, so profiles, photos and stories are never visible across families.

## Sensor Use

| Sensor | Feature it supports |
|---|---|
| **Camera** | Capturing new photos to add to the memory library |
| **Microphone** | Recording spoken stories for transcription, and audio messages that caregivers send to the patient, like a photo or a text message |
| **Location (Fused Location Provider API)** | Tagging memories with the place they were captured |

## Offline Mode

- **Viewing:** story and media metadata are cached by Firestore's built-in offline persistence. Photos and voice memos already opened or added on this phone are kept in an on-device media cache, which removes the least recently used ones first when it is full. This lets the patient keep browsing and reminiscing without a connection. **Coil** only decodes and displays these files; it doesn't download them or keep them on disk.
- **Adding:** a new photo or voice memo is first saved on the phone, in an **outbox** that is never cleared to free space, so it can be added offline. **WorkManager** sends it once connectivity returns, even if the app was closed in the meantime: the file goes to Firebase Storage before its metadata goes to Firestore, so a media item never points at a missing file.
- **Editing and deleting** a media item already sent need a connection; one not sent yet can be removed offline.
- **Privacy:** signing out or leaving a family erases its media from the device (cache and outbox). Media not sent yet would be lost, so the app warns first.
- Offline availability of the AI features depends on the model choice (see *Split-App Model*).

---

## Team

*TODO: team members and roles*
