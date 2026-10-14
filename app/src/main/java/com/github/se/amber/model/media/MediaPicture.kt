// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.media

import com.github.se.amber.model.circle.CareCircle

/**
 * What `MediaImage` asks Coil to show: [picture] of [circle]. The app's image loader gets its file
 * from the repository (`MediaPictureFetcher`) and keeps it decoded under its media id
 * (`MediaPictureKeyer`).
 */
data class MediaPicture(val circle: CareCircle, val picture: MediaItem.Picture)
