import { getStore } from "@netlify/blobs";

const STORE_NAME = "mock-test-studio-question-batches";
const json = (data, status = 200) => new Response(JSON.stringify(data), {
  status,
  headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "access-control-allow-origin": "same-origin" }
});
const norm = value => String(value ?? "").normalize("NFKC").toLowerCase().replace(/\s+/g, " ").trim();
function validateBank(raw) {
  const errors = [];
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return { errors: ["Top-level JSON must be an object."] };
  const subject = String(raw.subject || raw.name || "").trim();
  if (!subject) errors.push("Subject is required.");
  if (!Array.isArray(raw.chapters) || !raw.chapters.length) errors.push("A non-empty chapters array is required.");
  const chapterNames = new Set();
  let count = 0;
  for (const [ci, ch] of (Array.isArray(raw.chapters) ? raw.chapters : []).entries()) {
    const label = `Chapter ${ci + 1}`;
    if (!ch || typeof ch !== "object" || Array.isArray(ch)) { errors.push(`${label} must be an object.`); continue; }
    const title = String(ch.title || "").trim();
    if (!title) errors.push(`${label} needs a title.`);
    if (chapterNames.has(norm(title))) errors.push(`Repeated chapter title: ${title}.`);
    chapterNames.add(norm(title));
    if (!Array.isArray(ch.questions)) { errors.push(`${label} needs a questions array.`); continue; }
    const seen = new Map();
    for (const [qi, q] of ch.questions.entries()) {
      const qlabel = `${label}, question ${qi + 1}`;
      if (!q || typeof q !== "object" || Array.isArray(q)) { errors.push(`${qlabel} must be an object.`); continue; }
      const stem = String(q.question || "").trim();
      const opts = Array.isArray(q.options) ? Object.fromEntries(q.options.map((v,i)=>["ABCD"[i] || String(i+1),v])) : (q.options || {});
      if (!stem) errors.push(`${qlabel}: question text missing.`);
      for (const key of ["A","B","C","D"]) if (!String(opts[key] ?? "").trim()) errors.push(`${qlabel}: option ${key} missing.`);
      const answer = Array.isArray(q.answer) ? q.answer : [q.answer];
      if (answer.length !== 1 || !["A","B","C","D"].includes(String(answer[0] || "").trim().toUpperCase())) errors.push(`${qlabel}: answer must be one option key A/B/C/D.`);
      const identity = norm(stem) + "::" + ["A","B","C","D"].map(k=>k+"="+norm(opts[k])).join("|");
      const ans = String(answer[0] || "").trim().toUpperCase();
      if (seen.has(identity) && seen.get(identity) !== ans) errors.push(`${qlabel}: same question/options appear with a conflicting answer.`);
      seen.set(identity, ans);
      count++;
    }
  }
  if (count === 0) errors.push("No valid questions found.");
  return { errors, subject, count };
}
function authorized(request) {
  const expected = process.env.PUBLISHER_TOKEN;
  const supplied = request.headers.get("authorization") || "";
  return !!expected && supplied === `Bearer ${expected}`;
}
export default async (request) => {
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: { "access-control-allow-origin": "*", "access-control-allow-methods": "GET,POST,OPTIONS", "access-control-allow-headers": "content-type,authorization" } });
  const store = getStore(STORE_NAME);
  if (request.method === "GET") {
    try {
      const listed = await store.list({ prefix: "batches/" });
      const batches = [];
      for (const item of listed.blobs || []) {
        const value = await store.get(item.key, { type: "json" });
        if (value && value.status === "published") batches.push(value);
      }
      batches.sort((a,b) => String(a.published_at).localeCompare(String(b.published_at)));
      return json({ ok: true, batches });
    } catch (error) {
      return json({ ok: false, error: "Cloud storage is not ready. Confirm Netlify Blobs is enabled and redeploy." }, 503);
    }
  }
  if (request.method !== "POST") return json({ ok: false, error: "Method not allowed." }, 405);
  if (!authorized(request)) return json({ ok: false, error: "Publisher authentication failed. Check the Netlify PUBLISHER_TOKEN setting." }, 401);
  let body;
  try { body = await request.json(); } catch { return json({ ok: false, error: "Request body must be valid JSON." }, 400); }
  const checked = validateBank(body?.bank);
  if (checked.errors.length) return json({ ok: false, errors: checked.errors }, 422);
  const mode = body.mode === "merge" ? "merge" : "new";
  const target = String(body.target_subject || "").trim();
  if (mode === "merge" && !target) return json({ ok: false, error: "Choose the exact existing subject name for merge." }, 422);
  const id = String(body.batch_id || crypto.randomUUID());
  if (!/^[a-zA-Z0-9_-]{8,100}$/.test(id)) return json({ ok: false, error: "Invalid batch ID." }, 400);
  const batch = {
    batch_id: id, status: "published", published_at: new Date().toISOString(),
    mode, target_subject: mode === "merge" ? target : null,
    bank: body.bank, subject: checked.subject, question_count: checked.count,
    publisher_note: String(body.publisher_note || "").slice(0, 500)
  };
  try {
    const key = `batches/${id}.json`;
    const existing = await store.get(key, { type: "json" });
    if (existing) return json({ ok: true, already_published: true, batch_id: id });
    await store.setJSON(key, batch);
    return json({ ok: true, batch_id: id, question_count: checked.count, subject: checked.subject });
  } catch (error) {
    return json({ ok: false, error: "Could not save this batch to cloud storage." }, 500);
  }
};