// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

/** The Routes API key. It stays on the server, so the app never holds it. */
const routesApiKey = defineSecret("ROUTES_API_KEY");

const ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes";

/**
 * How long the Routes API may take. Shorter than the app's own wait (CALL_TIMEOUT_SECONDS in
 * FirebaseRouteRepository, 20 s), so the app gets "unavailable" and can fall back on the kept route.
 */
const ROUTES_TIMEOUT_MS = 15_000;

const MAX_LATITUDE = 90;
const MAX_LONGITUDE = 180;

/** Only what guidance uses: the API bills and sends less with a narrow mask. */
const FIELD_MASK = [
  "routes.distanceMeters",
  "routes.duration",
  "routes.polyline.geoJsonLinestring",
  "routes.legs.steps.distanceMeters",
  "routes.legs.steps.navigationInstruction.maneuver",
  "routes.legs.steps.startLocation",
  "routes.legs.steps.endLocation",
].join(",");

/**
 * @param {unknown} point what the app sent
 * @returns {boolean} whether it is exactly `{lat, lng}`, two numbers in range
 */
function isValidPoint(point) {
  return point !== null
    && typeof point === "object"
    && !Array.isArray(point)
    && Object.keys(point).length === 2
    && Number.isFinite(point.lat) && Math.abs(point.lat) <= MAX_LATITUDE
    && Number.isFinite(point.lng) && Math.abs(point.lng) <= MAX_LONGITUDE;
}

/** A point as the Routes API expects it. */
function waypoint({ lat, lng }) {
  return { location: { latLng: { latitude: lat, longitude: lng } } };
}

/**
 * A point of the Routes API as the app reads it. The API leaves out a coordinate that is 0, so a
 * missing one is 0; a missing location is an incomplete answer.
 */
function toPoint(location) {
  const latLng = location?.latLng;
  if (!latLng) {
    throw new HttpsError("internal", "The route service gave an incomplete route");
  }
  return { lat: latLng.latitude ?? 0, lng: latLng.longitude ?? 0 };
}

/**
 * The route as the app reads it. GeoJSON gives the line as [lng, lat] pairs, so the app needs no
 * polyline decoder.
 * @param {object} route the first route of the Routes API's answer
 * @returns {{distanceMeters: number, durationSeconds: number, points: object[], steps: object[]}}
 */
function toRoute(route) {
  const steps = (route.legs ?? []).flatMap((leg) => leg.steps ?? []);
  return {
    distanceMeters: route.distanceMeters ?? 0,
    // the API writes a duration as "123s"
    durationSeconds: Number.parseInt(route.duration ?? "0", 10) || 0,
    points: route.polyline.geoJsonLinestring.coordinates.map(([lng, lat]) => ({ lat, lng })),
    steps: steps.map((step) => ({
      maneuver: step.navigationInstruction?.maneuver ?? "MANEUVER_UNSPECIFIED",
      distanceMeters: step.distanceMeters ?? 0,
      start: toPoint(step.startLocation),
      end: toPoint(step.endLocation),
    })),
  };
}

/**
 * The walking route from `origin` to `destination`, from Google's Routes API.
 *
 * The points are never stored nor logged: the patient's position only passes through.
 * @param {CallableRequest} request whose data is exactly `{origin: {lat, lng}, destination: {lat, lng}}`
 * @returns {Promise<object>} see toRoute
 * @throws {HttpsError} unauthenticated, invalid-argument for any other data, not-found with reason
 *   NO_ROUTE when there is no walking route, unavailable when the Routes API can't answer in
 *   ROUTES_TIMEOUT_MS, or internal when a step of its answer has no location
 */
const getWalkingRoute = onCall({ secrets: [routesApiKey] }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in before asking for a route");
  }
  const data = request.data;
  if (
    !data
    || typeof data !== "object"
    || Object.keys(data).length !== 2
    || !isValidPoint(data.origin)
    || !isValidPoint(data.destination)
  ) {
    throw new HttpsError("invalid-argument", "A valid origin and destination are required");
  }

  let response;
  try {
    response = await fetch(ROUTES_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Goog-Api-Key": routesApiKey.value(),
        "X-Goog-FieldMask": FIELD_MASK,
      },
      body: JSON.stringify({
        origin: waypoint(data.origin),
        destination: waypoint(data.destination),
        travelMode: "WALK",
        polylineEncoding: "GEO_JSON_LINESTRING",
        units: "METRIC",
      }),
      signal: AbortSignal.timeout(ROUTES_TIMEOUT_MS),
    });
  } catch (error) {
    // a network error or the timeout: neither names the points
    console.error("Could not reach the Routes API", error);
    throw new HttpsError("unavailable", "The route service could not be reached");
  }
  if (!response.ok) {
    // Only the status: the message of the answer may repeat the request, and so the points
    const answer = await response.json().catch(() => null);
    console.error(`The Routes API refused: ${response.status} ${answer?.error?.status ?? ""}`);
    throw new HttpsError("unavailable", "The route service could not answer");
  }

  const route = (await response.json()).routes?.[0];
  if (!route?.polyline?.geoJsonLinestring?.coordinates?.length) {
    throw new HttpsError("not-found", "No walking route was found", { reason: "NO_ROUTE" });
  }
  return toRoute(route);
});

module.exports = { getWalkingRoute };
