// Written by Viktor Jurczenko, with assistance from
// Claude (Anthropic) via Claude Code.
package com.github.se.amber.model.location

/**
 * A place the patient often goes to (home, doctor, pharmacy...), shown on their map so they can
 * find their way.
 *
 * Stored: never as its own document. It is a value of CareCircle.places, keyed by its placeId, so
 * it lives in the circle's document at careCircles/{circleId}, field places.{placeId}.
 *
 * Written by caregivers only, one place at a time: update("places.{placeId}", place) to add or
 * change one, update("places.{placeId}", FieldValue.delete()) to remove it.
 *
 * Read: no query of its own. Places come with the CareCircle, already loaded and kept live by the
 * circle's snapshot listener: all places with circle.places.values, one with
 * circle.places[placeId].
 *
 * Choosing a place for an Appointment copies its [location] (with placeName = [name]) into
 * Appointment.location, so editing or deleting the place later doesn't change past appointments.
 */
data class Place(
    // placeId = key in CareCircle.places; a Firestore auto-id (repository newId())
    val id: String = "",
    val name: String = "", // label shown to the patient: "Home", "Dr. Martin"
    val location: GeoLocation = GeoLocation(),
)
