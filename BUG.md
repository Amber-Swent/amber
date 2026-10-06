# Bug Log

Record confirmed bugs discovered during implementation or review. Use the next available ID in the form `BUG-001`, `BUG-002`, and so on. Mark each entry `Open` or `Fixed`, and link the relevant source file, test, or PR when available. Do not add speculative issues.

| ID | Description | Status | Evidence / Fix |
| --- | --- | --- | --- |
| BUG-001 | `MediaFileCache.getFile` trimmed the whole cache after every call, including cache hits, listing the folder under the global lock and serializing image loads. | Fixed | Found in review of `app/src/main/java/com/github/se/amber/model/media/MediaFileCache.kt`; trim now runs only when a file was added. |
| BUG-002 | `MediaFileCache.trimToSize` sorted files by `lastModified()` read during the sort; concurrent cache hits change those times, which can make the sort throw `IllegalArgumentException` (32+ files). | Fixed | Found in review of `MediaFileCache.kt`; each file's time and size are now read once before sorting. |
| BUG-003 | `MediaFileCache.getFile` could return a file that a concurrent trim (started by another call) deleted right after. | Fixed | Found in review of `MediaFileCache.kt`; trim now skips files used since it listed them. |
| BUG-004 | `MediaFileCache` stopped caching (every miss and `put` threw `IOException`) if its folder was deleted while the app ran, e.g. by clearing the app's cache. | Fixed | Found in review of `MediaFileCache.kt`; the folder is recreated before each new file. Test: `cacheKeepsWorkingAfterItsFolderIsDeleted` in `MediaFileCacheTest.kt`. |
