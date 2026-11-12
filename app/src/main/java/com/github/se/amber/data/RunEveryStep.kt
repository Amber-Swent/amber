// Written by Alaabrine, with assistance from Cursor.

package com.github.se.amber.data

import kotlinx.coroutines.CancellationException

/**
 * Runs every one of [steps], even after one fails, then throws the first failure, with the later
 * ones added to it. A cancellation stops at once and is rethrown.
 */
internal suspend fun runEveryStep(vararg steps: suspend () -> Unit) {
  var first: Exception? = null
  for (step in steps) {
    try {
      step()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val earlier = first
      if (earlier == null) first = e else earlier.addSuppressed(e)
    }
  }
  first?.let { throw it }
}
