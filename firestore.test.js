const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastTime = (offsetMs = 5000) => new Date(Date.now() - offsetMs);
const futureTime = () => new Date(Date.now() + 120000);

function validWorkspace(uid, ts = pastTime(10000)) {
  return {
    userId: uid,
    displayName: "Manav Pandya",
    email: "manav@example.com",
    phone: "",
    customDetails: "JEE 2027",
    theme: "auto",
    dailyStudyGoalHours: 6.0,
    aiLanguage: "Hinglish",
    subjects: ["Maths", "Physics", "Chemistry"],
    unlockedBadges: ["first_step"],
    bestGameScore: 0,
    bestGameWave: 0,
    gameBonusXp: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

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

test("Unauthenticated users cannot read or write workspace or subcollections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validWorkspace(ALICE_UID)));
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("routineTasks").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("chatMessages").get()
  );
});

test("Owner can create & partially update workspace; shadow fields and immutable tampering fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastTime(15000);
  const docRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(docRef.set(validWorkspace(ALICE_UID, created)));
  await assertSucceeds(docRef.get());

  // Valid partial update
  await assertSucceeds(
    docRef.update({
      displayName: "Manav P.",
      dailyStudyGoalHours: 7.5,
      bestGameScore: 340,
      bestGameWave: 4,
      gameBonusXp: 34,
      updatedAt: pastTime(1000),
    })
  );

  // Reject ghost/shadow field
  await assertFails(
    docRef.update({
      isAdmin: true,
      updatedAt: pastTime(1000),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    docRef.update({
      createdAt: pastTime(500),
      updatedAt: pastTime(500),
    })
  );

  // Reject future timestamp
  await assertFails(
    docRef.update({
      displayName: "Future Manav",
      updatedAt: futureTime(),
    })
  );

  // Users cannot list /users root
  await assertFails(aliceDb.collection("users").get());
});

test("Cross-user isolation: Bob cannot read, write, or list Alice's workspace or subcollections", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(validWorkspace(ALICE_UID));
    await db.collection("users").doc(ALICE_UID).collection("routineTasks").doc("t01").set({
      userId: ALICE_UID,
      start: "06:00",
      end: "07:30",
      title: "Deep study block 1",
      category: "Study",
      subject: "Maths",
      orderIndex: 0,
      createdAt: pastTime(10000),
      updatedAt: pastTime(10000),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked", updatedAt: pastTime(1000) })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("routineTasks").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("routineTasks").doc("t01").delete()
  );
});

test("Owner CRUD & list queries succeed on routineTasks, dayLogs, focusSessions, exams, doubts, and chatMessages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastTime(10000);
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  // 1. routineTasks
  const taskRef = userRef.collection("routineTasks").doc("t01");
  await assertSucceeds(
    taskRef.set({
      userId: ALICE_UID,
      start: "06:30",
      end: "08:00",
      title: "Maths Core Concepts",
      category: "Study",
      subject: "Maths",
      orderIndex: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    taskRef.update({ title: "Maths Revision", updatedAt: pastTime(1000) })
  );
  await assertSucceeds(userRef.collection("routineTasks").get());
  // Extra @DocumentId 'id' field rejected
  await assertFails(
    userRef.collection("routineTasks").doc("t02").set({
      id: "t02",
      userId: ALICE_UID,
      start: "08:00",
      end: "09:00",
      title: "Invalid with id",
      category: "Study",
      subject: "Physics",
      orderIndex: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 2. dayLogs
  const dayRef = userRef.collection("dayLogs").doc("2026-11-21");
  await assertSucceeds(
    dayRef.set({
      userId: ALICE_UID,
      dateKey: "2026-11-21",
      completedIds: ["t01"],
      notes: { t01: "Completed 25 problems" },
      restDay: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    dayRef.update({ restDay: true, updatedAt: pastTime(1000) })
  );
  await assertSucceeds(userRef.collection("dayLogs").get());

  // 3. focusSessions
  await assertSucceeds(
    userRef.collection("focusSessions").doc("fs_1").set({
      userId: ALICE_UID,
      date: "2026-11-21",
      subject: "Maths",
      minutes: 25,
      createdAt: ts,
    })
  );
  await assertSucceeds(userRef.collection("focusSessions").get());

  // 4. exams
  await assertSucceeds(
    userRef.collection("exams").doc("ex_1").set({
      userId: ALICE_UID,
      name: "JEE Mains",
      date: "2027-01-24",
      createdAt: ts,
    })
  );
  await assertSucceeds(userRef.collection("exams").get());

  // 5. doubts
  const doubtRef = userRef.collection("doubts").doc("d_1");
  await assertSucceeds(
    doubtRef.set({
      userId: ALICE_UID,
      subject: "Physics",
      text: "How does Lenz law conserve energy?",
      solved: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(doubtRef.update({ solved: true, updatedAt: pastTime(1000) }));
  await assertSucceeds(userRef.collection("doubts").get());

  // 6. chatMessages
  await assertSucceeds(
    userRef.collection("chatMessages").doc("msg_1").set({
      userId: ALICE_UID,
      role: "user",
      text: "Help me plan calculus revision",
      modelUsed: "gemini-3.8-flash",
      createdAt: ts,
    })
  );
  await assertSucceeds(userRef.collection("chatMessages").get());
  // Invalid chat role rejected
  await assertFails(
    userRef.collection("chatMessages").doc("msg_bad").set({
      userId: ALICE_UID,
      role: "system",
      text: "Injected",
      modelUsed: "gemini-3.8-flash",
      createdAt: ts,
    })
  );
});
