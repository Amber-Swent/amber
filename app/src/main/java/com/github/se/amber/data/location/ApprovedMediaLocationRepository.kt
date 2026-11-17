// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.location

import com.github.se.amber.model.location.MediaMarker
import com.github.se.amber.model.location.toMediaMarkers
import com.github.se.amber.model.media.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The pins of the approved media ([MediaRepository.observeApprovedMedia]), the media the patient
 * may see. It adds no query of its own: the pins come from the same live list, and its offline
 * cache, as the patient's galleries.
 */
class ApprovedMediaLocationRepository(private val mediaRepository: MediaRepository) :
    MediaLocationRepository {

  override fun observeMarkers(circleId: String): Flow<List<MediaMarker>> =
      mediaRepository
          .observeApprovedMedia(circleId)
          .map { it.toMediaMarkers() }
          // A change to media without a point, e.g. a new text without a place, moves no pin
          .distinctUntilChanged()
}
