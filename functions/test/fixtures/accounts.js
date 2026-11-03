// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
const {state, storedBucket} = require("../support/fakes");

const BUCKET = "amber-family-test";
const EMPTY_BUCKET = "amber-family-alone";

/** The auth of a request from uid, signed in just now. */
function freshAuth(uid = "gone") {
  return {uid, token: {auth_time: Math.floor(Date.now() / 1000)}};
}

/** A photo by "other" in the shared circle, visible to everyone and showing nobody. */
function storedMedia(id, overrides = {}) {
  return {
    authorId: "other",
    storagePath: `careCircles/shared/media/${id}.jpg`,
    shownIds: [],
    authorizedIds: [],
    ...overrides,
  };
}

/**
 * Fills the in-memory Firestore, the buckets and Auth for deleteAccount. "gone" is deleted; "other"
 * and "patient" stay. "shared" is the circle of all three, "alone" has only "gone", and
 * "unrelated" only "other".
 */
function setupAccounts() {
  state.store = new Map(Object.entries({
    "users/gone": {uid: "gone", role: "CAREGIVER", circleIds: ["shared", "alone"]},
    "users/other": {uid: "other", role: "CAREGIVER", circleIds: ["shared", "unrelated"]},
    "users/patient": {uid: "patient", role: "PATIENT", circleIds: ["shared"]},
    "careCircles/shared": {
      memberIds: ["gone", "other", "patient"],
      patientId: "patient",
      createdBy: "gone",
      storageBucket: BUCKET,
      nicknames: {gone: {other: "Bob"}, other: {gone: "Al", patient: "Mum"}, patient: {gone: "Son"}},
    },
    "careCircles/shared/media/own-photo": storedMedia("own-photo", {authorId: "gone"}),
    "careCircles/shared/media/own-memo": storedMedia("own-memo", {
      authorId: "gone",
      storagePath: "careCircles/shared/media/own-memo.m4a",
    }),
    "careCircles/shared/media/own-text": storedMedia("own-text", {authorId: "gone", storagePath: ""}),
    "careCircles/shared/media/tagged": storedMedia("tagged", {shownIds: ["gone", "patient"]}),
    "careCircles/shared/media/restricted": storedMedia("restricted", {authorizedIds: ["gone"]}),
    "careCircles/shared/media/two-allowed": storedMedia("two-allowed", {authorizedIds: ["gone", "patient"]}),
    "careCircles/shared/stories/own-story": {createdBy: "gone", mediaIds: ["tagged"]},
    "careCircles/shared/stories/other-story": {
      createdBy: "other",
      mediaIds: ["own-photo", "tagged", "own-memo", "own-text"],
    },
    "careCircles/shared/appointments/doctor": {createdBy: "gone", attendeeIds: ["gone", "other"]},
    "careCircles/shared/appointments/physio": {createdBy: "other", attendeeIds: ["other"]},
    "careCircles/alone": {memberIds: ["gone"], patientId: "", createdBy: "gone", storageBucket: EMPTY_BUCKET},
    "careCircles/alone/media/photo": storedMedia("photo", {
      authorId: "gone",
      storagePath: "careCircles/alone/media/photo.jpg",
    }),
    "careCircles/unrelated": {memberIds: ["other"], patientId: "", createdBy: "other", storageBucket: "amber-other"},
    "careCircles/unrelated/media/photo": storedMedia("photo", {shownIds: ["other"]}),
    "invitations/created": {createdBy: "gone", usedBy: null, circleId: "shared"},
    "invitations/used": {createdBy: "other", usedBy: "gone", circleId: "shared"},
    "invitations/unrelated": {createdBy: "other", usedBy: "patient", circleId: "shared"},
  }));
  state.buckets = new Map([
    [BUCKET, new Set([
      "careCircles/shared/media/own-photo.jpg",
      "careCircles/shared/media/own-memo.m4a",
      "careCircles/shared/media/tagged.jpg",
    ])],
    [EMPTY_BUCKET, new Set(["careCircles/alone/media/photo.jpg"])],
    ["amber-other", new Set(["careCircles/unrelated/media/photo.jpg"])],
  ]);
  state.storage = {bucket: storedBucket};
  state.authUsers = new Set(["gone", "other", "patient"]);
}

module.exports = {BUCKET, EMPTY_BUCKET, freshAuth, storedMedia, setupAccounts};
