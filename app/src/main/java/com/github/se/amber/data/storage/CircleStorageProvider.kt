// Written by GitHub Copilot.
package com.github.se.amber.data.storage

import com.github.se.amber.model.circle.CareCircle
import com.google.firebase.storage.FirebaseStorage

/** Resolves the Firebase Storage instance assigned to a care circle. */
object CircleStorageProvider {
  fun forCircle(circle: CareCircle): FirebaseStorage {
    require(circle.storageBucket.isNotBlank()) {
      "Storage is not ready for care circle ${circle.id}"
    }
    return FirebaseStorage.getInstance("gs://${circle.storageBucket}")
  }
}
