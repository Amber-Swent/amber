// Written by Neha Chakraborty, with assistance from
// Claude (Anthropic) via Claude Code.
const { db } = require("./firebase");

// Below Firestore's limit of 500 writes in one batch
const BATCH_SIZE = 400;

/**
 * Writes one item after another in batches of BATCH_SIZE, committed in order. A failure stops at
 * that batch: the earlier ones stay written.
 * @param {Array} items what to write, one write per item
 * @param {(batch: WriteBatch, item: *) => void} write adds the write of one item to a batch
 */
async function writeInBatches(items, write) {
  for (let start = 0; start < items.length; start += BATCH_SIZE) {
    const batch = db.batch();
    items.slice(start, start + BATCH_SIZE).forEach((item) => write(batch, item));
    await batch.commit();
  }
}

/**
 * Deletes documents in batches, see writeInBatches.
 * @param {DocumentSnapshot[]} snapshots the documents to delete
 */
async function deleteDocuments(snapshots) {
  await writeInBatches(snapshots, (batch, snapshot) => batch.delete(snapshot.ref));
}

module.exports = { writeInBatches, deleteDocuments };
