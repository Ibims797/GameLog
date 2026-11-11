const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_nico_1";
const ADMIN_EMAIL = "nico.schaefer.hn@gmail.com";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read global_game_stats, user contributions, or admin_vault", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("global_game_stats").where("isCommunityEntry", "==", true).get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("game_contributions").get()
  );
  await assertFails(
    unauthDb.collection("admin_vault").doc("api_secrets").get()
  );
});

test("Regular authenticated user CANNOT read or write admin_vault API keys", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("admin_vault").doc("api_secrets").set({
      twitchClientId: "secret_twitch_id",
      twitchClientSecret: "secret_twitch_secret",
      steamApiKey: "secret_steam_key",
      updatedByUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  // Normal user cannot read API keys
  await assertFails(aliceDb.collection("admin_vault").doc("api_secrets").get());

  // Normal user cannot overwrite API keys
  await assertFails(
    aliceDb.collection("admin_vault").doc("api_secrets").update({
      steamApiKey: "hacked_key",
      updatedByUid: ALICE_UID,
      updatedAt: new Date(),
    })
  );
});

test("Email spoofing attack (unverified admin email) CANNOT read or write admin_vault", async () => {
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(spoofedAdminDb.collection("admin_vault").doc("api_secrets").get());
});

test("Verified Admin CAN create, read, and update admin_vault API keys", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const now = new Date();
  const vaultRef = adminDb.collection("admin_vault").doc("api_secrets");

  await assertSucceeds(
    vaultRef.set({
      twitchClientId: "my_twitch_client_id",
      twitchClientSecret: "my_twitch_client_secret",
      steamApiKey: "my_steam_api_key",
      updatedByUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(vaultRef.get());

  await assertSucceeds(
    vaultRef.update({
      twitchClientSecret: "rotated_secret_value",
      updatedByUid: ADMIN_UID,
      updatedAt: new Date(),
    })
  );
});

test("Authenticated user can create, update, and query global_game_stats with matching filter and anonymous star counts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const statsRef = aliceDb.collection("global_game_stats").doc("igdb_1942");

  await assertSucceeds(
    statsRef.set({
      title: "The Witcher 3: Wild Hunt",
      coverImageUrl: "https://images.igdb.com/igdb/image/upload/t_cover_big/co1wyy.jpg",
      averageRating: 5.0,
      ratingSum: 5,
      ratingCount: 1,
      recommendCount: 1,
      total100PercentRuns: 1,
      star5Count: 1,
      star4Count: 0,
      star3Count: 0,
      star2Count: 0,
      star1Count: 0,
      isCommunityEntry: true,
      lastUpdatedBy: ALICE_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("global_game_stats").where("isCommunityEntry", "==", true).get()
  );
});
