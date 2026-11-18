const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_01";
const BOB_UID = "bob_uid_02";
const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

function validProfilePayload(uid) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    nickname: "ViperAce",
    skinColorIndex: 2,
    outfitColorIndex: 4,
    cash: 1500,
    unlockedWeapons: ["MELEE", "PISTOL"],
    graphicsQuality: "MEDIUM",
    cameraSensitivity: 1.0,
    audioVolume: 0.8,
    createdAt: now,
    updatedAt: now,
  };
}

function validRoomPayload(hostUid, visibility = "PUBLIC") {
  const now = new Date(Date.now() - 1000);
  return {
    hostUid,
    name: "Neon Harbor Free Roam",
    visibility,
    maxPlayers: 24,
    pvpEnabled: true,
    playerCount: 1,
    createdAt: now,
    updatedAt: now,
  };
}

function validPlayerPayload(uid, roomId) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    roomId,
    nickname: "ViperAce",
    skinColorIndex: 1,
    outfitColorIndex: 3,
    x: 10.5,
    y: 0.0,
    z: -22.0,
    yaw: 90.0,
    speed: 6.5,
    animState: "RUN",
    vehicleType: "NONE",
    vehicleHp: 100,
    hp: 100,
    armor: 50,
    wantedStars: 0,
    weaponIndex: 1,
    inSafeZone: true,
    shotSeq: 0,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated users cannot read or write users or rooms", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
  await assertFails(
    unauth.collection("rooms").where("visibility", "==", "PUBLIC").get()
  );
});

test("User profile enforces owner-only create, partial update, and rejects ghost fields", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );

  // Partial update allowed fields
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      cash: 2500,
      unlockedWeapons: ["MELEE", "PISTOL", "SMG"],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user read/write blocked
  await assertFails(bob.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bob.collection("users").doc(ALICE_UID).update({ cash: 999999 })
  );

  // Ghost field & createdAt tamper blocked
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Multiplayer rooms enforce filtered list queries, player ownership, and immutable events", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();

  // Alice creates a public room and private room
  await assertSucceeds(
    alice.collection("rooms").doc("room_pub").set(validRoomPayload(ALICE_UID, "PUBLIC"))
  );
  await assertSucceeds(
    alice.collection("rooms").doc("room_priv").set(validRoomPayload(ALICE_UID, "PRIVATE"))
  );

  // Filtered PUBLIC list query succeeds for Bob; unfiltered list fails
  await assertSucceeds(
    bob.collection("rooms").where("visibility", "==", "PUBLIC").get()
  );
  await assertFails(bob.collection("rooms").get());

  // Bob joins room_pub as his own player document
  await assertSucceeds(
    bob
      .collection("rooms")
      .doc("room_pub")
      .collection("players")
      .doc(BOB_UID)
      .set(validPlayerPayload(BOB_UID, "room_pub"))
  );

  // Bob cannot spoof Alice's player document
  await assertFails(
    bob
      .collection("rooms")
      .doc("room_pub")
      .collection("players")
      .doc(ALICE_UID)
      .set(validPlayerPayload(ALICE_UID, "room_pub"))
  );

  // Bob updates his own transform and queries players with roomId filter
  await assertSucceeds(
    bob
      .collection("rooms")
      .doc("room_pub")
      .collection("players")
      .doc(BOB_UID)
      .update({
        x: 42.0,
        z: 18.5,
        vehicleType: "SEDAN",
        updatedAt: new Date(Date.now() - 200),
      })
  );
  await assertSucceeds(
    alice
      .collection("rooms")
      .doc("room_pub")
      .collection("players")
      .where("roomId", "==", "room_pub")
      .get()
  );

  // Events: create succeeds, update/delete fail
  await assertSucceeds(
    bob
      .collection("rooms")
      .doc("room_pub")
      .collection("events")
      .doc("evt_1")
      .set({
        roomId: "room_pub",
        senderId: BOB_UID,
        senderName: "BobRider",
        eventType: "CHAT",
        message: "Ayo balapan di jembatan!",
        createdAt: new Date(Date.now() - 200),
      })
  );
  await assertFails(
    bob
      .collection("rooms")
      .doc("room_pub")
      .collection("events")
      .doc("evt_1")
      .update({ message: "Edited" })
  );
});
