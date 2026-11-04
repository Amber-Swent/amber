// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
const { getAuth } = require("firebase-admin/auth");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { deleteDocuments } = require("./batches");
const { removeFromCircle } = require("./circleCleanup");
const { db } = require("./firebase");

const RECENT_SIGN_IN_SECONDS = 5 * 60;
const FIRESTORE_NOT_FOUND = 5;
// The app waits a little longer for the answer: see CALL_TIMEOUT_SECONDS in
// FirebaseAccountDeletionRepository
const DELETE_ACCOUNT_TIMEOUT_SECONDS = 540;

/**
 * Refuses a sign-in older than RECENT_SIGN_IN_SECONDS. The Admin SDK's deleteUser doesn't check
 * this itself.
 * @param {object | undefined} token the caller's decoded ID token, from request.auth.token
 * @throws {HttpsError} failed-precondition with `details.reason` RECENT_SIGN_IN_REQUIRED, which
 *   clients map, if auth_time is missing, malformed or too old
 */
function requireRecentSignIn(token) {
  const signedInSeconds = token?.auth_time;
  if (
    !Number.isFinite(signedInSeconds)
    || Date.now() / 1000 - signedInSeconds > RECENT_SIGN_IN_SECONDS
  ) {
    throw new HttpsError("failed-precondition", "Sign in again before deleting your account", {
      reason: "RECENT_SIGN_IN_REQUIRED",
    });
  }
}

/**
 * Marks the profile as being deleted, which makes redeemInvitation refuse this user.
 *
 * Comes first: redeemInvitation reads the profile in its transaction, so once this is written no
 * redemption can add a circle that the query in deleteAccount would miss. Stays set after a partial
 * failure, as the account is then half deleted. The profile is already gone on some retries.
 * @param {string} uid the user being deleted
 */
async function markDeletionRequested(uid) {
  try {
    await db.doc(`users/${uid}`).update({ deletionRequested: true });
  } catch (error) {
    if (error.code !== FIRESTORE_NOT_FOUND) {
      throw error;
    }
  }
}

/**
 * Deletes the invitations the user created or redeemed.
 * @param {string} uid the user being deleted
 */
async function deleteInvitations(uid) {
  const invitations = db.collection("invitations");
  const [created, used] = await Promise.all([
    invitations.where("createdBy", "==", uid).get(),
    invitations.where("usedBy", "==", uid).get(),
  ]);
  await deleteDocuments([...created.docs, ...used.docs]);
}

/**
 * Deletes the user from Firebase Authentication. A user already deleted counts as done.
 * @param {string} uid the user being deleted
 */
async function deleteAuthUser(uid) {
  try {
    await getAuth().deleteUser(uid);
  } catch (error) {
    // already deleted by an earlier, retried run
    if (error.code !== "auth/user-not-found") {
      throw error;
    }
  }
}

/**
 * Permanently deletes the caller's account and personal data. Every step can run again, so a client
 * can simply retry after a failure. Only ever acts on request.auth.uid: the request data is ignored.
 *
 * 1. Mark the profile: from then on redeemInvitation refuses this user, so no circle is missed.
 * 2. Delete their invitations, so nobody joins a circle that is about to be deleted.
 * 3. For each circle they are a member of:
 *    a. If others stay, refresh the bucket's Storage rules, so the user can't add files meanwhile.
 *       Alone in the circle, the whole bucket is about to go.
 *    b. Delete their media with its files, and their stories. Their media is removed from the
 *       other stories first: its ids are only known while its documents exist. The files go
 *       before their documents, the only record of where they are.
 *    c. Remove them from what stays: the other media and the appointments.
 *    d. Only then remove them from memberIds. A retry finds the circles to clean through
 *       memberIds: had they left a circle first and a later step failed, the retry would never
 *       come back to it.
 *    e. If they were its last member, delete the bucket, then the circle. The bucket goes first,
 *       as its name would be lost with the circle.
 * 4. Delete the profile, then the Auth user.
 * @param {CallableRequest} request needs a recent sign-in, see requireRecentSignIn
 * @returns {Promise<{deleted: true}>} once everything is deleted
 * @throws {HttpsError} unauthenticated without a signed-in caller, or failed-precondition
 *   (RECENT_SIGN_IN_REQUIRED) for an old sign-in, before anything is written
 */
const deleteAccount = onCall({ timeoutSeconds: DELETE_ACCOUNT_TIMEOUT_SECONDS }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in before deleting your account");
  }
  requireRecentSignIn(request.auth.token);

  const uid = request.auth.uid;
  await markDeletionRequested(uid);
  await deleteInvitations(uid);
  const circles = await db.collection("careCircles").where("memberIds", "array-contains", uid).get();
  for (const circle of circles.docs) {
    await removeFromCircle(uid, circle);
  }
  await db.doc(`users/${uid}`).delete();
  await deleteAuthUser(uid);
  return { deleted: true };
});

module.exports = { deleteAccount };
