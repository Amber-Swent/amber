# Amber

> Reminiscence and cognitive stimulation for people living with dementia, and peace of mind for the people who care for them.

**Figma:** [Design mockups](https://www.figma.com/design/wHW7M7HObl4S22BM02lFiK/View?node-id=0-1&t=pVwS4cfDWgUvTzio-1)

---

## App Description

Over 50 million people worldwide live with dementia, facing cognitive decline, short-term memory loss and severe isolation, while their families carry an intense psychological and financial burden. Dementia is irreversible, but structured social engagement can ease its symptoms and help preserve a person's sense of self. **Amber** is an AI-powered Android app built on two clinically recognised approaches, Cognitive Stimulation Therapy and Reminiscence Therapy. Patients revisit their life through interactive photo storytelling and speak their memories aloud, which are transcribed with voice-to-text. Caregivers get a dashboard to curate photos and follow engagement.

Amber targets two user groups, represented by two fictional personas: patients in the early-to-moderate stages of dementia, like **Arthur**, who need a simple, frustration-free way to reminisce and stay mentally active, and caregivers (family members and other loved ones), like **Sarah**, who need a reliable tool to manage photos, monitor engagement and lighten a 24/7 caregiving load.

### Personas

*The personas below are fictional examples that represent our target users; they are not real people.*

| | Arthur: Patient persona | Sarah: Caregiver persona |
|---|---|---|
| **Age** | 78 | 45 |
| **Situation** | Early-to-moderate stage Alzheimer's | Overworked family caregiver |
| **Needs** | An easy, frustration-free way to reminisce and stay mentally active | A reliable dashboard to manage photos, monitor engagement and reduce her caregiving burden |
| **Uses Amber to** | Browse photo stories, tell and record memories by voice | Upload and organise photos, follow the patient's activity |

---

## Split-App Model

Amber has **no self-hosted backend or custom server code**. It is a fully native Android client that talks directly to managed cloud services:

| Service | Role |
|---|---|
| **Firebase Authentication** | User identification, login flows and role management (Caregiver / Patient) |
| **Cloud Firestore** | Storage of user profiles, roles, family groups, stories and engagement data, protected by Firestore Security Rules |

> **AI implementation: to be decided.** The AI features (voice-to-text transcription and photo storytelling assistance) are part of the core design, but the model and how it will be integrated (managed cloud API vs. on-device model) have not been chosen yet. This section will be updated once the choice is made.

## Multi-User Support

- **Authentication:** Firebase Authentication provides secure sign-in.
- **Roles:** each account is either a **Caregiver** (family members and other loved ones included) or a **Patient**, with different permissions and interfaces.
- **Isolation:** Firestore Security Rules restrict each family's data to its own members, so profiles, photos and stories are never visible across families.

## Sensor Use

| Sensor | Feature it supports |
|---|---|
| **Camera** | Capturing new photos to add to the memory library |
| **Microphone** | Recording spoken stories for transcription, and audio messages that caregivers send to the patient, like a photo or a text message |
| **Location (Fused Location Provider API)** | Tagging memories with the place they were captured |

## Offline Mode

- Photos and stories are cached locally with a **Room** database, so the patient can keep browsing and reminiscing without a connection.
- New photos, recordings and other changes made offline are **queued locally** and **synchronised automatically** once connectivity returns.
- Offline availability of the AI features depends on the model choice (see *Split-App Model*).

---

## Team

*TODO: team members and roles*
