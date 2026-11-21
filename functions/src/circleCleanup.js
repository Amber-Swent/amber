// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
// Modified by Alaabrine, with assistance from Cursor (Grok).
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
const { FieldValue } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");
const { deleteDocuments, writeInBatches } = require("./batches");
const { db } = require("./firebase");
const { deleteBucket, ensureFamilyBucketRules, usesStorage } = require("./familyBucket");

/**
 * Deletes the media the user authored in a circle, with their files, and the user's stories.
 * Removes those media from the stories of the other members.
 *
 * Stories go first: the ids to remove are only known while the media documents still exist.
 * @param {string} uid the user being deleted
 * @param {DocumentReference} circleRef the circle to clean up
 * @param {string | undefined} bucketName the circle's storageBucket
 */
async function deleteAuthoredMedia(uid, circleRef, bucketName) {
  const media = await circleRef.collection("media").where("authorId", "==", uid).get();
  const stories = await circleRef.collection("stories").get();
  const mediaIds = new Set(media.docs.map((item) => item.id));

  const ownStories = stories.docs.filter((story) => story.get("createdBy") === uid);
  await deleteDocuments(ownStories);
  const otherStories = stories.docs
    .filter((story) => story.get("createdBy") !== uid)
    .map((story) => ({
      ref: story.ref,
      ids: (story.get("mediaIds") ?? []).filter((id) => mediaIds.has(id)),
    }))
    .filter((story) => story.ids.length > 0);
  await writeInBatches(otherStories, (batch, story) =>
    batch.update(story.ref, { mediaIds: FieldValue.arrayRemove(...story.ids) }));

  // The files go before their documents, the only record of where they are
  if (usesStorage(bucketName)) {
    const bucket = getStorage().bucket(bucketName);
    for (const item of media.docs) {
      const storagePath = item.get("storagePath");
      if (storagePath) {
        await bucket.file(storagePath).delete({ ignoreNotFound: true });
      }
    }
  }
  await deleteDocuments(media.docs);
}

/**
 * Removes the user from the people shown in, and allowed to see, the media of the other members.
 * @param {string} uid the user being deleted
 * @param {DocumentReference} circleRef the circle to clean up
 */
async function removeFromRemainingMedia(uid, circleRef) {
  const media = circleRef.collection("media");
  const [shown, authorized] = await Promise.all([
    media.where("shownIds", "array-contains", uid).get(),
    media.where("authorizedIds", "array-contains", uid).get(),
  ]);
  await writeInBatches(shown.docs, (batch, item) =>
    batch.update(item.ref, { shownIds: FieldValue.arrayRemove(uid) }));
  // An empty list means the whole circle can see the media: keep it restricted to its author
  await writeInBatches(authorized.docs, (batch, item) => {
    const isOnlyOneAllowed = item.get("authorizedIds").length === 1;
    batch.update(item.ref, {
      authorizedIds: isOnlyOneAllowed ? [item.get("authorId")] : FieldValue.arrayRemove(uid),
    });
  });
}

/**
 * Deletes the appointments the user created in a circle. Removes the user from the attendees of the
 * others, and empties their last editor where it was the user.
 * @param {string} uid the user being deleted
 * @param {DocumentReference} circleRef the circle to clean up
 */
async function removeFromAppointments(uid, circleRef) {
  const appointments = circleRef.collection("appointments");
  const [created, attending, updated] = await Promise.all([
    appointments.where("createdBy", "==", uid).get(),
    appointments.where("attendeeIds", "array-contains", uid).get(),
    appointments.where("updatedBy", "==", uid).get(),
  ]);
  await deleteDocuments(created.docs);
  // An update of a deleted appointment would fail, so only the others are updated
  const createdIds = new Set(created.docs.map((appointment) => appointment.id));
  const others = (snapshot) =>
    snapshot.docs.filter((appointment) => !createdIds.has(appointment.id));
  await writeInBatches(others(attending), (batch, appointment) =>
    batch.update(appointment.ref, { attendeeIds: FieldValue.arrayRemove(uid) }));
  await writeInBatches(others(updated), (batch, appointment) =>
    batch.update(appointment.ref, { updatedBy: "" }));
}

/**
 * Removes a user from a circle's nicknames: the ones they gave, and the ones given to them.
 * @param {string} uid the user being deleted
 * @param {Object<string, Object<string, string>>} nicknames by viewer, then by member nicknamed
 * @returns {Object<string, Object<string, string>>} the nicknames without the user
 */
function nicknamesWithout(uid, nicknames) {
  return Object.fromEntries(
    Object.entries(nicknames)
      .filter(([viewerId]) => viewerId !== uid)
      .map(([viewerId, viewerNicknames]) => [
        viewerId,
        Object.fromEntries(Object.entries(viewerNicknames).filter(([id]) => id !== uid)),
      ]),
  );
}

/**
 * Removes the user's key from a circle's personPhotos. Every other person's photo stays, including
 * a person who has no account.
 * @param {string} uid the user being deleted
 * @param {Object<string, string>} personPhotos person id to storage path
 * @returns {Object<string, string>} the photos without the user
 */
function personPhotosWithout(uid, personPhotos) {
  return Object.fromEntries(
    Object.entries(personPhotos).filter(([personId]) => personId !== uid),
  );
}

/**
 * Removes the user from a circle's members, nicknames, personPhotos, patient and creator, and the
 * circle from their profile, in one transaction. Leaves both as they are if they are its last
 * member: the circle is then deleted as a whole, see forgetCircle. An absent personPhotos field
 * stays absent.
 *
 * The profile must only list circles the app can read, ones that exist and list the user: the
 * deletion screen reads every circle of the profile before letting the user retry.
 * @param {string} uid the user being deleted
 * @param {DocumentReference} circleRef the circle to leave
 * @returns {Promise<boolean>} whether they were its last member
 */
async function leaveCircle(uid, circleRef) {
  const profileRef = db.doc(`users/${uid}`);
  return db.runTransaction(async (transaction) => {
    const [circleSnapshot, profile] = await Promise.all([
      transaction.get(circleRef),
      transaction.get(profileRef),
    ]);
    // a double tap: another call deleted it first
    if (!circleSnapshot.exists) {
      return false;
    }
    const circle = circleSnapshot.data();
    if (circle.memberIds.every((memberId) => memberId === uid)) {
      return true;
    }
    transaction.update(circleRef, {
      memberIds: FieldValue.arrayRemove(uid),
      ...(circle.nicknames ? { nicknames: nicknamesWithout(uid, circle.nicknames) } : {}),
      ...(circle.personPhotos
        ? { personPhotos: personPhotosWithout(uid, circle.personPhotos) }
        : {}),
      ...(circle.patientId === uid ? { patientId: "" } : {}),
      ...(circle.createdBy === uid ? { createdBy: "" } : {}),
    });
    if (profile.exists) {
      transaction.update(profileRef, { circleIds: FieldValue.arrayRemove(circleRef.id) });
    }
    return false;
  });
}

/**
 * Removes a circle about to be deleted from the user's profile, as leaveCircle does for a circle
 * that stays.
 * @param {string} uid the user being deleted
 * @param {string} circleId the circle about to be deleted
 */
async function forgetCircle(uid, circleId) {
  const profileRef = db.doc(`users/${uid}`);
  await db.runTransaction(async (transaction) => {
    const profile = await transaction.get(profileRef);
    if (profile.exists) {
      transaction.update(profileRef, { circleIds: FieldValue.arrayRemove(circleId) });
    }
  });
}

/**
 * Releases the current Storage rules on a circle's bucket, which refuse files from a user being
 * deleted. Does nothing when usesStorage is false.
 *
 * Rules are only released when a bucket is created, so an older bucket may let the user being
 * deleted add files. Deletion carries on without it: a late upload is less serious than an account
 * that can't be deleted.
 * @param {string} circleId the circle, for the log
 * @param {string | undefined} bucketName the circle's storageBucket
 */
async function refreshBucketRules(circleId, bucketName) {
  if (!usesStorage(bucketName)) {
    return;
  }
  try {
    await ensureFamilyBucketRules(bucketName);
  } catch (error) {
    console.warn(`Could not refresh the Storage rules of circle ${circleId}`, error);
  }
}

/**
 * Deletes careCircles/{circleId}/people/{uid}/ when others stay in the circle. The whole prefix
 * goes, including files personPhotos no longer lists. Not another member's folder, not a person
 * without an account, and not media. A missing prefix deletes nothing. Does nothing when
 * usesStorage is false: the emulator has no bucket, and this would reach real Cloud Storage.
 * @param {string} uid the user being deleted
 * @param {string} circleId the circle that stays
 * @param {string | undefined} bucketName the circle's storageBucket
 */
async function deletePhotoFolder(uid, circleId, bucketName) {
  if (!usesStorage(bucketName)) {
    return;
  }
  await getStorage().bucket(bucketName).deleteFiles({
    prefix: `careCircles/${circleId}/people/${uid}/`,
  });
}

/**
 * Removes every trace of the user from one circle, and deletes the circle with its bucket if they
 * were its last member. Step 3 of deleteAccount, which explains the order.
 * @param {string} uid the user being deleted
 * @param {DocumentSnapshot} circleSnapshot a circle that still lists them in memberIds
 */
async function removeFromCircle(uid, circleSnapshot) {
  const circleRef = circleSnapshot.ref;
  const bucketName = circleSnapshot.get("storageBucket");
  const othersStay = circleSnapshot.get("memberIds").length > 1;
  // Alone in the circle, the whole bucket is about to go
  if (othersStay) {
    await refreshBucketRules(circleRef.id, bucketName);
  }
  await deleteAuthoredMedia(uid, circleRef, bucketName);
  await removeFromRemainingMedia(uid, circleRef);
  await removeFromAppointments(uid, circleRef);
  // The folder goes before leaveCircle drops their personPhotos entry
  if (othersStay) {
    await deletePhotoFolder(uid, circleRef.id, bucketName);
  }
  if (await leaveCircle(uid, circleRef)) {
    await deleteBucket(bucketName);
    // Before the circle goes: a profile listing a deleted circle would block the deletion screen,
    // while a retry still finds a circle the profile no longer lists, through memberIds
    await forgetCircle(uid, circleRef.id);
    await db.recursiveDelete(circleRef);
  }
}

module.exports = { removeFromCircle };
