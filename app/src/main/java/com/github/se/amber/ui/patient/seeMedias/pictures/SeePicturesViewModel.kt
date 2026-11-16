// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.pictures

import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.circle.CareCircleRepositoryProvider
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MediaRepositoryProvider
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasErrors
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasViewModel

private val PICTURES_ERRORS =
    SeeGeneralMediasErrors(
        loadProfile = R.string.seePicturesErrorLoadProfile,
        noProfile = R.string.seePicturesErrorNoProfile,
        noCircle = R.string.seePicturesErrorNoCircle,
        loadCircle = R.string.seePicturesErrorLoadCircle,
        loadMedia = R.string.seePicturesErrorLoadMedia,
    )

/**
 * Loads the patient's approved pictures. All the loading logic is in [SeeGeneralMediasViewModel].
 */
class SeePicturesViewModel(
    mediaRepository: MediaRepository = MediaRepositoryProvider.repository,
    circleRepository: CareCircleRepository = CareCircleRepositoryProvider.default.repository,
    profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
) :
    SeeGeneralMediasViewModel<MediaItem.Picture>(
        mediaRepository = mediaRepository,
        circleRepository = circleRepository,
        profileRepository = profileRepository,
        errors = PICTURES_ERRORS,
    ) {

  init {
    load()
  }

  override fun filterMedia(media: List<MediaItem>): List<MediaItem.Picture> =
      media.filterIsInstance<MediaItem.Picture>()
}
