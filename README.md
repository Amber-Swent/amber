# Amber

> Reminiscence and cognitive stimulation for people living with dementia, and peace of mind for the people who care for them.

**Figma:** [Design mockups](https://www.figma.com/design/wHW7M7HObl4S22BM02lFiK/View?node-id=0-1&t=pVwS4cfDWgUvTzio-1)

---

## App Description

Over 50 million people worldwide live with dementia, facing cognitive decline, short-term memory loss and severe isolation, while their families carry an intense psychological and financial burden. Dementia is irreversible, but structured social engagement can ease its symptoms and help preserve a person's sense of self. **Amber** is an Android app built on two clinically recognised approaches, Cognitive Stimulation Therapy and Reminiscence Therapy. Patients revisit their life through interactive photo storytelling. Family members and other loved ones capture memories on their phone: they take photos with the **camera**, record voice memos with the **microphone**, and **location** tagging anchors each memory to where it happened.

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

## Research Background

Amber is a digital form of **reminiscence therapy**: it uses personal photos and voice memos as cues to evoke autobiographical memories. Our design choices are based on the scientific literature summarised below. Amber is meant to *support* and *complement* care, not to treat or slow the disease.

**Target.** Patients in the early to moderate stage of dementia, mainly Alzheimer's disease (the most common form, roughly 40-60% of cases), with vascular dementia and Lewy body dementia as secondary and tertiary targets.

### What the evidence supports

- **Remote memories are preserved longer.** Autobiographical memories often remain accessible after recent memory has declined, so reminiscence draws on abilities that still work instead of asking the patient to learn something new.
- **Recommended, with modest benefits.** Clinical guidelines recommend considering reminiscence therapy for early to moderate dementia. Systematic reviews report small positive effects on cognition, mood, communication and quality of life, and no evidence of harm. These effects are modest and do not always last without continued stimulation.
- **Multisensory, personalised cues work best.** Photos, voices and places from the person's own life, revisited regularly.
- **Emotion outlasts memory.** Patients can stay happier after a pleasant experience even if they forget it.
- **Families matter.** Involving relatives improves quality of life, and social isolation increases dementia risk.

### Known risks and how Amber addresses them

We also looked specifically for drawbacks. The evidence suggests that viewing photos does not directly "damage" memories (there is no direct evidence of this in dementia), but photos can interact with vulnerabilities that are already present.

| Risk | Why it matters | Design response |
|---|---|---|
| **Source confusion / false memories** | In Alzheimer's disease, people may struggle to tell what they remembered from what they were told (e.g. a caregiver's caption). | Patients only browse; all content is added by caregivers. Captions should stay factual (correct dates and places), and information from caregivers should remain distinguishable from the patient's own memories. |
| **Distressing photos** | Emotions can persist after the event is forgotten; photos of bereavement or conflict may upset. | Only circle members can add content. Moderation (approval before the patient sees a memory) and photo/word filtering are **planned**. |
| **Pressure to perform** | Memory tests ("Don't you remember?") can cause frustration and a feeling of failure. | The patient experience is browsing, looking and listening, with no scoring and no correction. Any future quiz would be optional and low-pressure. |
| **Temporal confusion and grief** | Old events may feel recent, so photos of deceased relatives can resurface grief. | **Planned:** caregivers can hide photos that repeatedly cause distress. |

**Confidence.** The idea that a reactivated memory becomes temporarily modifiable (reconsolidation) is a theoretical mechanism, supported by experiments on healthy adults but not by studies in dementia. Source confusion is the better-supported risk, which is why we focus on it.

**Sources.** This section summarises the scientific literature we consulted but does not list references. If you would like the sources behind a specific claim, please contact us and we will be happy to share them.


## Split-App Model

Amber has **no separately hosted backend**. It is a fully native Android client backed by managed Firebase services, including project-owned server-side Cloud Functions:

| Service | Role |
|---|---|
| **Firebase Authentication** | User identification, login flows and role management (Caregiver / Patient) |
| **Cloud Firestore** | Storage of user profiles, roles, care circles and memberships, invitations, stories, appointments, and *metadata* about media (description, author, `storagePath`, …), but not the files themselves; access is protected by Firestore Security Rules |
| **Firebase Storage** | Storage of photos and voice memos (Firestore only keeps their paths) |
| **Firebase Cloud Functions** | Server-side operations for redeeming invitations, deleting accounts and their associated data, and provisioning a dedicated Storage bucket for each care circle |

## Multi-User Support

- **Authentication:** Firebase Authentication provides secure sign-in.
- **Roles:** each account is either a **Caregiver** (family members and other loved ones included) or a **Patient**, with different permissions and interfaces.
- **Isolation:** Firestore and Firebase Storage Security Rules restrict each family's data to its own members, so profiles, photos and stories are never visible across families.

## Using Amber

1. Open Amber and tap **Sign in with Google**. On your first sign-in, create a profile with your name and surname, then choose **Caregiver** or **Patient**.
2. Connect to a care circle:
   - As a **Caregiver**, create a care circle or enter an invitation code to join an existing one.
   - As a **Patient**, enter an invitation code from a caregiver to join their circle.
   - To invite someone, a caregiver can open **Parameters > Create Invitation**, generate a code, and share it. Each code can be used once and expires after 30 days.
3. As a **Caregiver**, open **Upload > Upload Picture** to take a picture with the in-app camera or choose one from the phone's gallery. Add a description and any relevant details, then tap **Upload Picture**. Uploads can be queued offline and sent when the phone reconnects.
4. As a **Caregiver**, open **Upload > Upload Audio** to record a voice memo, add its description and other relevant details, then upload it to the circle.
5. As a **Patient**, open **See Medias > See Pictures** to browse shared pictures, or **See Medias > See Audio** to listen to shared voice recordings.
6. As a **Caregiver**, delete the Amber account associated with your Google sign-in. Account deletion is currently available to caregivers only.

## Sensor Use

Caregivers can use the phone's camera and microphone to add memories to a care circle. Place tagging is optional and lets patients revisit memories by where they happened.

| Sensor or feature | How Amber uses it                                                                                                                                                                             |
|---|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Camera** | Caregivers can take a picture in the app or choose an existing one from the phone's gallery before uploading it.                                                                              |
| **Microphone** | Caregivers can record a voice memo in the app and upload it with a description for the circle.                                                                                                |
| **Place search** | Caregivers can tag a picture with a saved place or search for a place by name or address. Amber uses Android's Geocoder to suggest places; it does not read the phone's GPS location for now. |

Amber asks for microphone permission the first time a caregiver records a voice memo. Taking a picture opens the phone’s own camera app. Searching for place suggestions requires a network connection.

## Offline Mode

- **Viewing:** story and media metadata are cached by Firestore's built-in offline persistence. Photos and voice memos already opened or added on this phone are kept in an on-device media cache, which removes the least recently used ones first when it is full. This lets the patient keep browsing and reminiscing without a connection. **Coil** only decodes and displays these files; it doesn't download them or keep them on disk.
- **Adding:** a new photo or voice memo is first saved on the phone, in an **outbox** that is never cleared to free space, so it can be added offline. **WorkManager** sends it once connectivity returns, even if the app was closed in the meantime: the file goes to Firebase Storage before its metadata goes to Firestore, so a media item never points at a missing file.
- **Editing and deleting** a media item already sent need a connection; one not sent yet can be removed offline.
- **Privacy:** signing out or leaving a family erases its media from the device (cache and outbox). Media not sent yet would be lost, so the app warns first.

---

## Team

| Member          | Role                             |
|-----------------|----------------------------------|
| LaMaier         | Frontend + support Communication |
| nehachak1       | Backend + support Frontend       |
| ViktorJurczenko | Backend                          |
| MeiliV          | Frontend + Communication         |
| Alaabrine       | Backend + Frontend               |
| feiyang2521     | Frontend                         |
| camomille299    | Frontend + Communication         |

