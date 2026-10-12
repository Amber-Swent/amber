# Amber

> Reminiscence and cognitive stimulation for people living with dementia, and peace of mind for the people who care for them.

**Figma:** [Design mockups](https://www.figma.com/design/wHW7M7HObl4S22BM02lFiK/View?node-id=0-1&t=pVwS4cfDWgUvTzio-1)

---

## App Description

Over 50 million people worldwide live with dementia, facing cognitive decline, short-term memory loss and severe isolation, while their families carry an intense psychological and financial burden. Dementia is irreversible, but structured social engagement can ease its symptoms and help preserve a person's sense of self. **Amber** is an Android app built on two clinically recognised approaches, Cognitive Stimulation Therapy and Reminiscence Therapy. Patients revisit their life through interactive photo storytelling. Family members and other loved ones capture memories straight from their phone: they take photos with the **camera**, record voice memos with the **microphone**, and **location** tagging anchors each memory to the place it happened, so the patient can browse stories by person, time and place.

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

## Multi-User Support

- **Authentication:** Firebase Authentication provides secure sign-in.
- **Roles:** each account is either a **Caregiver** (family members and other loved ones included) or a **Patient**, with different permissions and interfaces.
- **Isolation:** Firestore and Firebase Storage Security Rules restrict each family's data to its own members, so profiles, photos and stories are never visible across families.

## Sensor Use

The phone's sensors are at the heart of how memories are created and revisited in Amber:

| Sensor | Feature it supports |
|---|---|
| **Camera** | Caregivers capture new photos (or digitise old printed ones) directly in the app to add to the family's memory library |
| **Microphone** | Recording voice memos of shared memories (other microphone-based features may be added later) |
| **Location (Fused Location Provider API)** | Memories are tagged with the place they were captured, so the patient can revisit them by place (e.g. "our house by the lake") |

## Offline Mode

- Story metadata is cached by Firestore's built-in offline persistence, and photos are cached on disk by **Coil**, so the patient can keep browsing and reminiscing without a connection.
- Changes made offline are **queued locally** and **synchronised automatically** once connectivity returns: Firestore queues its own writes, and photo and voice memo uploads to Firebase Storage are retried in the background.

---

## Team

*TODO: team members and roles*
