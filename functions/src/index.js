"use strict";

const { initializeApp } = require("firebase-admin/app");
const { FieldValue, Timestamp, getFirestore } = require("firebase-admin/firestore");
const { HttpsError, onCall } = require("firebase-functions/v2/https");

initializeApp();
const db = getFirestore();
const REGION = "us-central1";
const CALLABLE_OPTIONS = { region: REGION, enforceAppCheck: true, maxInstances: 10 };

function uidFor(request) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in to use Zeex.");
  return request.auth.uid;
}

function collection(uid, name) {
  return db.collection("users").doc(uid).collection(name);
}

function text(value, name, max = 4000) {
  if (typeof value !== "string" || !value.trim() || value.length > max) {
    throw new HttpsError("invalid-argument", `${name} must be 1-${max} characters.`);
  }
  return value.trim();
}

function id(value) {
  if (typeof value !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(value)) {
    throw new HttpsError("invalid-argument", "A valid record id is required.");
  }
  return value;
}

function millis(value, name, { future = false } = {}) {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number <= 0 || (future && number <= Date.now())) {
    throw new HttpsError("invalid-argument", `${name} must be a valid${future ? " future" : ""} timestamp in milliseconds.`);
  }
  return number;
}

function docToJson(doc) {
  const data = doc.data();
  for (const key of ["createdAt", "updatedAt", "scheduledAt"]) {
    if (data[key] instanceof Timestamp) data[key] = data[key].toMillis();
  }
  return { id: doc.id, ...data };
}

async function list(col, limit = 200) {
  const snapshot = await col.orderBy("createdAt", "desc").limit(limit).get();
  return snapshot.docs.map(docToJson);
}

exports.loadZeexData = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = uidFor(request);
  const [chat, missions, reminders] = await Promise.all([
    list(collection(uid, "chat")),
    list(collection(uid, "missions")),
    list(collection(uid, "reminders")),
  ]);
  return { chat: chat.reverse(), missions, reminders };
});

exports.saveChat = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = uidFor(request);
  const data = request.data || {};
  const recordId = id(data.id);
  if (typeof data.fromUser !== "boolean") throw new HttpsError("invalid-argument", "fromUser must be boolean.");
  const message = text(data.text, "text");
  const createdAt = millis(data.createdAt, "createdAt");
  await collection(uid, "chat").doc(recordId).set({
    fromUser: data.fromUser,
    text: message,
    createdAt: Timestamp.fromMillis(createdAt),
  }, { merge: true });
  return { ok: true };
});

exports.clearChat = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = uidFor(request);
  const col = collection(uid, "chat");
  while (true) {
    const snapshot = await col.limit(400).get();
    if (snapshot.empty) break;
    const batch = db.batch();
    snapshot.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
  }
  return { ok: true };
});

exports.createReminder = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = uidFor(request);
  const data = request.data || {};
  const recordId = id(data.id);
  const title = text(data.title, "title", 180);
  const scheduledAt = millis(data.scheduledAt, "scheduledAt", { future: true });
  const now = FieldValue.serverTimestamp();
  await collection(uid, "reminders").doc(recordId).create({ title, scheduledAt: Timestamp.fromMillis(scheduledAt), completed: false, createdAt: now, updatedAt: now });
  return { id: recordId, title, scheduledAt, completed: false };
});

exports.updateReminder = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = uidFor(request);
  const data = request.data || {};
  const recordId = id(data.id);
  const title = text(data.title, "title", 180);
  const scheduledAt = millis(data.scheduledAt, "scheduledAt", { future: true });
  const ref = collection(uid, "reminders").doc(recordId);
  const snapshot = await ref.get();
  if (!snapshot.exists) throw new HttpsError("not-found", "Reminder not found.");
  await ref.update({ title, scheduledAt: Timestamp.fromMillis(scheduledAt), updatedAt: FieldValue.serverTimestamp() });
  return { id: recordId, title, scheduledAt };
});

exports.cancelReminder = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = uidFor(request);
  await collection(uid, "reminders").doc(id(request.data?.id)).delete();
  return { ok: true };
});

exports.createMission = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = uidFor(request);
  const data = request.data || {};
  const recordId = id(data.id);
  const title = text(data.title, "title", 180);
  const goal = text(data.goal, "goal", 1000);
  const now = FieldValue.serverTimestamp();
  await collection(uid, "missions").doc(recordId).create({ title, goal, status: "ACTIVE", createdAt: now, updatedAt: now });
  return { id: recordId, title, goal, status: "ACTIVE" };
});

exports.setMissionStatus = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = uidFor(request);
  const data = request.data || {};
  const status = data.status;
  if (!["ACTIVE", "COMPLETED", "PAUSED"].includes(status)) {
    throw new HttpsError("invalid-argument", "status must be ACTIVE, PAUSED, or COMPLETED.");
  }
  const ref = collection(uid, "missions").doc(id(data.id));
  const snapshot = await ref.get();
  if (!snapshot.exists) throw new HttpsError("not-found", "Mission not found.");
  await ref.update({ status, updatedAt: FieldValue.serverTimestamp() });
  return { ok: true, status };
});

