// Written by Alaabrine, with assistance from Cursor (Grok).

package com.github.se.amber.data.circle

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Stores the active care circle in Preferences DataStore, one string per uid.
 *
 * Reading applies the circle list it is given: a stored id that is no longer in that list falls
 * back to the first non-blank id and is left in storage, so the choice returns if the circle comes
 * back. [clear] is the only way the choice is removed. A read that fails with [IOException] is
 * treated as no stored choice, so the same fallback applies.
 */
class DataStoreActiveCircleRepository(
    private val dataStore: DataStore<Preferences>,
) : ActiveCircleRepository {

  override fun activeCircleId(uid: String, circleIds: List<String>): Flow<String?> {
    if (uid.isBlank()) return flowOf(null)
    val key = key(uid)
    return dataStore.data
        .catch { exception ->
          if (exception is IOException) {
            emit(emptyPreferences())
          } else {
            throw exception
          }
        }
        .map { preferences -> resolve(preferences[key], circleIds) }
        .distinctUntilChanged()
  }

  override suspend fun setActiveCircle(uid: String, id: String) {
    if (uid.isBlank() || id.isBlank()) return
    dataStore.edit { preferences -> preferences[key(uid)] = id }
  }

  override suspend fun clear(uid: String) {
    if (uid.isBlank()) return
    dataStore.edit { preferences -> preferences.remove(key(uid)) }
  }

  private fun key(uid: String) = stringPreferencesKey(KEY_PREFIX + uid)

  /**
   * The stored id when it is still a usable circle, otherwise the first non-blank id. A blank
   * stored value is treated as no choice.
   */
  private fun resolve(stored: String?, circleIds: List<String>): String? {
    val usable = circleIds.filter { it.isNotBlank() }
    if (!stored.isNullOrBlank() && stored in usable) return stored
    return usable.firstOrNull()
  }

  private companion object {
    const val KEY_PREFIX = "active_circle_"
  }
}
