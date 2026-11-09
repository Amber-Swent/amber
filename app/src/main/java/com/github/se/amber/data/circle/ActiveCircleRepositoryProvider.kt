// Written by Alaabrine, with assistance from Cursor (Grok).

package com.github.se.amber.data.circle

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.Firebase
import com.google.firebase.app

private val Context.activeCircleDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "active_circle")

/**
 * Holds a single [ActiveCircleRepository], built lazily by `factory` on first access.
 *
 * ViewModels should receive the repository as a constructor parameter (their factory reads it from
 * [default]), so tests can pass a fake instead. With the default factory, the default Firebase app
 * must be initialized before [repository] is first accessed; if building fails, the next access
 * tries again.
 */
class ActiveCircleRepositoryProvider(
    factory: () -> ActiveCircleRepository = { firebaseRepository() },
) {
  val repository: ActiveCircleRepository by lazy(factory)

  companion object {
    /** The app-wide instance. Don't use it in tests. */
    val default = ActiveCircleRepositoryProvider()

    /** Builds the DataStore repository for [context]. Tests pass their own context. */
    internal fun firebaseRepository(
        context: Context = Firebase.app.applicationContext,
    ): ActiveCircleRepository = DataStoreActiveCircleRepository(context.activeCircleDataStore)
  }
}
