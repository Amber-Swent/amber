// Written by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
const assert = require("node:assert/strict");
const test = require("node:test");
const {callableModule, resetState} = require("./support/fakes");
const {expectHttpsError} = require("./support/assertions");

/**
 * The route the function returns, as the app reads it. The app's FirebaseRouteRepositoryTest reads
 * the same file, so both sides agree on the format.
 */
const ROUTE = require("./fixtures/walkingRoute.json");

/** Lausanne Flon and the cathedral, a short walk apart: where ROUTE starts and ends. */
const ORIGIN = ROUTE.steps[0].start;
const DESTINATION = ROUTE.steps.at(-1).end;
const REQUEST = {origin: ORIGIN, destination: DESTINATION};
const SIGNED_IN = {uid: "patient-1"};

const MAX_LATITUDE = 90;
const MAX_LONGITUDE = 180;

/** A point as the Routes API writes it. */
function latLng({lat, lng}) {
  return {latLng: {latitude: lat, longitude: lng}};
}

/** A step of ROUTE as the Routes API writes it. */
function apiStep({maneuver, distanceMeters, start, end}) {
  return {
    distanceMeters,
    navigationInstruction: {maneuver},
    startLocation: latLng(start),
    endLocation: latLng(end),
  };
}

/**
 * The Routes API's answer for ROUTE, as the field mask asks for it: one leg per step, so the test
 * also checks that the steps of every leg are joined in order.
 */
function routesAnswer(steps = ROUTE.steps.map(apiStep)) {
  return {
    routes: [{
      distanceMeters: ROUTE.distanceMeters,
      duration: `${ROUTE.durationSeconds}s`,
      polyline: {geoJsonLinestring: {
        type: "LineString",
        coordinates: ROUTE.points.map(({lat, lng}) => [lng, lat]),
      }},
      legs: steps.map((step) => ({steps: [step]})),
    }],
  };
}

const realFetch = globalThis.fetch;
const realConsoleError = console.error;
let routesRequests;
let logged;

/** Makes the Routes API answer with status and body, or throw when answer is an Error. */
function routesApiAnswers(answer, status = 200) {
  globalThis.fetch = async (url, init) => {
    routesRequests.push({url, init});
    if (answer instanceof Error) throw answer;
    return {
      ok: status >= 200 && status < 300,
      status,
      json: async () => answer,
    };
  };
}

/**
 * Called first by each top-level test. Not a beforeEach hook: those also run before each subtest,
 * which would clear the requests and logs a test checks after its subtests.
 */
function freshState() {
  resetState();
  routesRequests = [];
  logged = [];
  console.error = (...args) => logged.push(args.map(String).join(" "));
}

test.after(() => {
  globalThis.fetch = realFetch;
  console.error = realConsoleError;
});

test("rejects signed-out and malformed requests without calling the Routes API", async (t) => {
  freshState();
  routesApiAnswers(routesAnswer());
  const {getWalkingRoute} = callableModule();
  await t.test("requires sign-in", async () => {
    await expectHttpsError(
      getWalkingRoute({data: REQUEST}),
      "unauthenticated",
      "Sign in before asking for a route",
    );
  });

  const cases = [
    ["no data", undefined],
    ["no destination", {origin: ORIGIN}],
    ["an extra field", {...REQUEST, mode: "DRIVE"}],
    ["a point with an extra field", {origin: {...ORIGIN, alt: 0}, destination: DESTINATION}],
    ["a latitude above the largest", {origin: {...ORIGIN, lat: MAX_LATITUDE + 0.5}, destination: DESTINATION}],
    ["a latitude below the smallest", {origin: {...ORIGIN, lat: -MAX_LATITUDE - 0.5}, destination: DESTINATION}],
    ["a longitude above the largest", {origin: ORIGIN, destination: {...DESTINATION, lng: MAX_LONGITUDE + 1}}],
    ["a longitude below the smallest", {origin: ORIGIN, destination: {...DESTINATION, lng: -MAX_LONGITUDE - 1}}],
    ["a coordinate as text", {origin: {...ORIGIN, lat: String(ORIGIN.lat)}, destination: DESTINATION}],
    ["a coordinate that is not a number", {origin: {...ORIGIN, lng: Number.NaN}, destination: DESTINATION}],
    ["a point as a list", {origin: [ORIGIN.lat, ORIGIN.lng], destination: DESTINATION}],
  ];
  for (const [name, data] of cases) {
    await t.test(`rejects ${name}`, async () => {
      await expectHttpsError(
        getWalkingRoute({auth: SIGNED_IN, data}),
        "invalid-argument",
        "A valid origin and destination are required",
      );
    });
  }
  assert.deepEqual(routesRequests, []);

  await t.test("accepts points exactly on the limits", async () => {
    await getWalkingRoute({auth: SIGNED_IN, data: {
      origin: {lat: MAX_LATITUDE, lng: -MAX_LONGITUDE},
      destination: {lat: -MAX_LATITUDE, lng: MAX_LONGITUDE},
    }});
    assert.equal(routesRequests.length, 1);
  });
});

test("asks for a walking route with the server's key and returns it as the app reads it", async () => {
  freshState();
  routesApiAnswers(routesAnswer());
  const {getWalkingRoute} = callableModule();

  const route = await getWalkingRoute({auth: SIGNED_IN, data: REQUEST});

  const [{url, init}] = routesRequests;
  assert.equal(url, "https://routes.googleapis.com/directions/v2:computeRoutes");
  assert.equal(init.headers["X-Goog-Api-Key"], "test-routes-key");
  assert.match(init.headers["X-Goog-FieldMask"], /routes\.legs\.steps\.navigationInstruction\.maneuver/);
  // gives up before the app does, so the app can still use the kept route
  assert.ok(init.signal instanceof AbortSignal);
  const body = JSON.parse(init.body);
  assert.equal(body.travelMode, "WALK");
  assert.deepEqual(body.origin.location, latLng(ORIGIN));
  assert.deepEqual(body.destination.location, latLng(DESTINATION));
  // GeoJSON's [lng, lat] turned into {lat, lng}, and the steps of every leg in order
  assert.deepEqual(route, ROUTE);
});

test("reads the zero values the Routes API leaves out, and refuses a step without a location", async (t) => {
  freshState();
  const [first, last] = ROUTE.steps;
  await t.test("no instruction, no distance, a latitude of 0", async () => {
    const onTheEquator = {...last.end, lat: 0};
    routesApiAnswers(routesAnswer([
      apiStep(first),
      {startLocation: latLng(last.start), endLocation: {latLng: {longitude: onTheEquator.lng}}},
    ]));
    const {getWalkingRoute} = callableModule();

    const route = await getWalkingRoute({auth: SIGNED_IN, data: REQUEST});

    assert.deepEqual(route.steps.at(-1), {
      maneuver: "MANEUVER_UNSPECIFIED", distanceMeters: 0, start: last.start, end: onTheEquator,
    });
  });
  await t.test("a step without a location", async () => {
    const {endLocation, ...noEnd} = apiStep(last);
    routesApiAnswers(routesAnswer([apiStep(first), noEnd]));
    const {getWalkingRoute} = callableModule();
    await expectHttpsError(
      getWalkingRoute({auth: SIGNED_IN, data: REQUEST}),
      "internal",
      "The route service gave an incomplete route",
    );
  });
});

test("says when there is no walking route", async (t) => {
  freshState();
  const answers = [
    ["no routes", {}],
    ["an empty list", {routes: []}],
    ["a route without a line", {routes: [{distanceMeters: ROUTE.distanceMeters}]}],
  ];
  for (const [name, answer] of answers) {
    await t.test(name, async () => {
      routesApiAnswers(answer);
      const {getWalkingRoute} = callableModule();
      await expectHttpsError(
        getWalkingRoute({auth: SIGNED_IN, data: REQUEST}),
        "not-found",
        "No walking route was found",
        "NO_ROUTE",
      );
    });
  }
});

test("reports the Routes API failing as unavailable, without logging the points", async (t) => {
  freshState();
  await t.test("unreachable or too slow", async () => {
    routesApiAnswers(new DOMException("The operation was aborted due to timeout", "TimeoutError"));
    const {getWalkingRoute} = callableModule();
    await expectHttpsError(
      getWalkingRoute({auth: SIGNED_IN, data: REQUEST}),
      "unavailable",
      "The route service could not be reached",
    );
  });
  await t.test("refusing, with a message that repeats the request", async () => {
    const message = `Invalid origin ${ORIGIN.lat},${ORIGIN.lng} to ${DESTINATION.lat},${DESTINATION.lng}`;
    routesApiAnswers({error: {status: "INVALID_ARGUMENT", message}}, 400);
    const {getWalkingRoute} = callableModule();
    await expectHttpsError(
      getWalkingRoute({auth: SIGNED_IN, data: REQUEST}),
      "unavailable",
      "The route service could not answer",
    );
  });

  const logs = logged.join("\n");
  assert.match(logs, /400 INVALID_ARGUMENT/);
  for (const coordinate of [ORIGIN.lat, ORIGIN.lng, DESTINATION.lat, DESTINATION.lng]) {
    assert.equal(logs.includes(String(coordinate)), false, logs);
  }
});
