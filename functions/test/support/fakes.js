// Written by GitHub Copilot.
// Modified by Neha Chakraborty and Copilot, with assistance from
// Claude (Anthropic) via Claude Code.
const assert = require("node:assert/strict");
const Module = require("node:module");
const path = require("node:path");

/** Stands in for the HttpsError of firebase-functions, keeping what the clients read. */
class MockHttpsError extends Error {
  constructor(code, message, details) {
    super(message);
    this.code = code;
    this.details = details;
  }
}

/**
 * What the fakes read and record, reset before each test by resetState. The redemption and
 * provisioning tests use documents, circleSnapshots, updates and creates; the deleteAccount tests
 * use store, buckets, authUsers, beforeWrite, failWrite and batchWrites.
 */
const state = {
  documents: new Map(),
  storage: null,
  securityRules: null,
  bucketLocation: "europe-west6",
  circleSnapshots: [],
  circleReadIndex: 0,
  updates: [],
  creates: [],
  linkRequests: [],
  linkStatus: 200,
  store: null,
  buckets: null,
  authUsers: null,
  beforeWrite: null,
  failWrite: null,
  batchWrites: [],
};

// the only HTTP call the functions make: linking a bucket to Firebase Storage
globalThis.fetch = async (url, init) => {
  state.linkRequests.push({url, init});
  // same rule as real fetch: ok only for 2xx
  return {ok: state.linkStatus >= 200 && state.linkStatus < 300, status: state.linkStatus, text: async () => "link refused"};
};

/** A document snapshot holding value, missing when value is undefined. */
function snapshot(value) {
  return {
    exists: value !== undefined,
    data: () => value,
    get: (field) => value?.[field],
  };
}

/** Empties state, with a Storage that has no bucket and Security Rules with no ruleset yet. */
function resetState() {
  state.documents = new Map();
  state.updates = [];
  state.creates = [];
  state.linkRequests = [];
  state.linkStatus = 200;
  state.circleSnapshots = [];
  state.circleReadIndex = 0;
  state.store = null;
  state.buckets = null;
  state.authUsers = null;
  state.beforeWrite = null;
  state.failWrite = null;
  state.batchWrites = [];
  state.storage = {
    bucket: () => {
      throw new Error("Storage bucket was not configured");
    },
  };
  state.securityRules = {
    getStorageRuleset: async () => {
      throw Object.assign(new Error("Missing ruleset"), {code: "security-rules/not-found"});
    },
    createRulesFileFromSource: (name, source) => ({name, source}),
    createRuleset: async (rulesFile) => ({source: [rulesFile]}),
    releaseStorageRuleset: async () => {},
  };
}

// An in-memory Firestore for deleteAccount, which needs real queries and writes.

/** The new value of a field: arrayRemove is applied to the current one, any other value replaces it. */
function applyField(current, value) {
  if (value?.arrayRemove) return (current ?? []).filter((item) => !value.arrayRemove.includes(item));
  return value;
}

/**
 * Throws `Failed <action> <target>` if state.failWrite returns true for this write, so a test can
 * match the failure it injected.
 */
function failIfAsked(action, target) {
  if (state.failWrite?.(action, target)) throw new Error(`Failed ${action} ${target}`);
}

/**
 * Applies one write to state.store. Calls state.beforeWrite first, then throws if failIfAsked does,
 * and, like Firestore, when updating a missing document.
 * @param {"update" | "delete"} action
 */
function write(action, documentPath, data) {
  state.beforeWrite?.(action, documentPath);
  failIfAsked(action, documentPath);
  if (action === "delete") {
    state.store.delete(documentPath);
    return;
  }
  const current = state.store.get(documentPath);
  if (!current) throw Object.assign(new Error(`No document ${documentPath}`), {code: 5});
  const updated = {...current};
  for (const [field, value] of Object.entries(data)) updated[field] = applyField(current[field], value);
  state.store.set(documentPath, updated);
}

/** A snapshot of the stored document, with its id and ref like a real query result. */
function storedSnapshot(documentPath) {
  return {
    ...snapshot(state.store.get(documentPath)),
    id: documentPath.split("/").at(-1),
    ref: storedDocument(documentPath),
  };
}

/**
 * A collection of state.store whose where() supports "==" and "array-contains", the only operators
 * deleteAccount uses. Only lists direct children, like Firestore.
 */
function storedCollection(collectionPath, filters = []) {
  return {
    where: (field, operator, value) => storedCollection(
      collectionPath,
      [...filters, (data) => (operator === "==" ? data[field] === value : data[field]?.includes(value))],
    ),
    get: async () => ({
      docs: [...state.store.keys()]
        .filter((key) => key.slice(0, key.lastIndexOf("/")) === collectionPath)
        .filter((key) => filters.every((filter) => filter(state.store.get(key))))
        .map(storedSnapshot),
    }),
  };
}

/** A reference to a document of state.store. */
function storedDocument(documentPath) {
  return {
    path: documentPath,
    collection: (name) => storedCollection(`${documentPath}/${name}`),
    id: documentPath.split("/").at(-1),
    update: async (data) => write("update", documentPath, data),
    delete: async () => write("delete", documentPath),
  };
}

/**
 * An in-memory Firestore over state.store, for deleteAccount, which needs real queries and writes.
 * Batches are atomic and limited to 500 writes like the real ones, and record their writes in
 * state.batchWrites.
 */
function storedFirestore() {
  return {
    doc: storedDocument,
    collection: storedCollection,
    // like the real one, nothing is written if any write of the batch fails
    batch: () => {
      const writes = [];
      return {
        update: (reference, data) => writes.push(["update", reference.path, data]),
        delete: (reference) => writes.push(["delete", reference.path]),
        commit: async () => {
          assert.ok(writes.length <= 500, "Firestore refuses a batch above 500 writes");
          state.batchWrites.push(writes.map(([, documentPath]) => documentPath));
          const before = new Map(state.store);
          try {
            writes.forEach((entry) => write(...entry));
          } catch (error) {
            state.store = before;
            throw error;
          }
        },
      };
    },
    runTransaction: async (callback) => {
      const writes = [];
      const result = await callback({
        get: async (reference) => storedSnapshot(reference.path),
        update: (reference, data) => writes.push(["update", reference.path, data]),
      });
      writes.forEach((entry) => write(...entry));
      return result;
    },
    recursiveDelete: async (reference) => {
      failIfAsked("recursiveDelete", reference.path);
      for (const key of [...state.store.keys()]) {
        if (key === reference.path || key.startsWith(`${reference.path}/`)) state.store.delete(key);
      }
    },
  };
}

/**
 * The Firestore of the redemption and provisioning tests, which records writes rather than storing
 * them: transactions read state.documents and record in state.updates and state.creates, and a
 * document read outside them returns the next of state.circleSnapshots.
 */
function recordingFirestore() {
  return {
    doc: (documentPath) => ({
      path: documentPath,
      get: async () => {
        const circle = state.circleSnapshots[state.circleReadIndex];
        state.circleReadIndex += 1;
        return snapshot(circle);
      },
      update: async (data) => state.updates.push({reference: "circle", data}),
    }),
    runTransaction: async (callback) =>
      callback({
        get: async (reference) => snapshot(state.documents.get(reference.path)),
        update: (reference, data) => state.updates.push({reference: {path: reference.path}, data}),
        create: (reference, data) => state.creates.push({reference: {path: reference.path}, data}),
      }),
  };
}

/** A Cloud Storage bucket whose files are the paths in state.buckets.get(name). */
function storedBucket(name) {
  const notFound = () => Object.assign(new Error(`No bucket ${name}`), {code: 404});
  return {
    file: (filePath) => ({
      delete: async ({ignoreNotFound}) => {
        failIfAsked("deleteFile", filePath);
        if (!state.buckets.get(name)?.delete(filePath) && !ignoreNotFound) throw notFound();
      },
    }),
    deleteFiles: async ({force}) => {
      assert.equal(force, true);
      if (!state.buckets.has(name)) throw notFound();
      state.buckets.get(name).clear();
    },
    delete: async ({ignoreNotFound}) => {
      failIfAsked("deleteBucket", name);
      if (!state.buckets.delete(name) && !ignoreNotFound) throw notFound();
    },
  };
}

/**
 * Loads index.js afresh with every Firebase module replaced by the fakes above, so it uses the
 * state of the current test.
 * @param {object} [options]
 * @param {object} [options.firestore] the Firestore the functions get: recordingFirestore() unless
 *   given, storedFirestore() for deleteAccount
 * @returns the exported functions, each being its bare handler
 */
function callableModule({firestore = recordingFirestore()} = {}) {
  const originalLoad = Module._load;
  Module._load = function mockModuleLoad(request, parent, isMain) {
    if (request === "firebase-admin/app") {
      return {
        initializeApp: () => ({
          options: {credential: {getAccessToken: async () => ({access_token: "test-token"})}},
        }),
      };
    }
    if (request === "firebase-admin/auth") {
      return {
        getAuth: () => ({
          deleteUser: async (uid) => {
            failIfAsked("deleteUser", uid);
            if (!state.authUsers.delete(uid)) {
              throw Object.assign(new Error("No user"), {code: "auth/user-not-found"});
            }
          },
        }),
      };
    }
    if (request === "firebase-admin/firestore") {
      return {
        FieldValue: {
          arrayUnion: (value) => ({arrayUnion: value}),
          arrayRemove: (...values) => ({arrayRemove: values}),
        },
        getFirestore: () => firestore,
      };
    }
    if (request === "firebase-admin/storage") return {getStorage: () => state.storage};
    if (request === "firebase-admin/security-rules") {
      return {getSecurityRules: () => state.securityRules};
    }
    if (request === "firebase-functions/params") {
      return {defineString: () => ({value: () => state.bucketLocation})};
    }
    if (request === "firebase-functions/v2/https") {
      // the handler comes last, after the options if there are any
      return {HttpsError: MockHttpsError, onCall: (...args) => args.at(-1)};
    }
    if (request === "firebase-functions/v2/firestore") {
      return {onDocumentCreated: (_options, handler) => handler};
    }
    return originalLoad.call(this, request, parent, isMain);
  };

  // every module of the functions, not only index.js: each would otherwise keep the fakes it first
  // got, such as the db of src/firebase.js. Not the tests' own files, which share this state.
  const indexPath = path.join(__dirname, "..", "..", "index.js");
  const sourceDirectory = path.join(__dirname, "..", "..", "src") + path.sep;
  for (const cachedPath of Object.keys(require.cache)) {
    if (cachedPath === indexPath || cachedPath.startsWith(sourceDirectory)) {
      delete require.cache[cachedPath];
    }
  }
  try {
    return require(indexPath);
  } finally {
    Module._load = originalLoad;
  }
}

module.exports = {MockHttpsError, state, resetState, storedBucket, storedFirestore, callableModule};
