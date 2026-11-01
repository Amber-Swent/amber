package com.github.se.amber.ui.caregiver.upload.media

/** Start of the label of a member whose profile could not be read, instead of showing their uid. */
const val UNKNOWN_MEMBER_LABEL = "Unknown member"

/**
 * Label of the [number]-th member (from 1) whose profile could not be read, so that several of them
 * can be told apart.
 */
fun unknownMemberLabel(number: Int): String = "$UNKNOWN_MEMBER_LABEL $number"
