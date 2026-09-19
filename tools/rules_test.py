"""Runs the app's writes against the Firestore emulator to prove the rules accept them (and block abuse)."""
import base64, json, time, urllib.request, urllib.error

P = "demo-facebooky"
BASE = f"http://127.0.0.1:8080/v1/projects/{P}/databases/(default)/documents"
failures = []

def b64(d): return base64.urlsafe_b64encode(json.dumps(d).encode()).decode().rstrip("=")

def token(uid):
    now = int(time.time())
    return b64({"alg": "none", "typ": "JWT"}) + "." + b64({
        "sub": uid, "user_id": uid, "iss": f"https://securetoken.google.com/{P}", "aud": P,
        "iat": now, "exp": now + 3600, "auth_time": now,
        "firebase": {"sign_in_provider": "anonymous", "identities": {}}}) + "."

def req(method, url, uid, body=None):
    h = {"Content-Type": "application/json", "Authorization": f"Bearer {token(uid)}"}
    r = urllib.request.Request(url, data=json.dumps(body).encode() if body else None, method=method, headers=h)
    try:
        with urllib.request.urlopen(r) as resp: return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e: return e.code, e.read().decode()

def S(v): return {"stringValue": v}
def I(v): return {"integerValue": str(v)}

def write(uid, path, fields, ts_field=None):
    w = {"update": {"name": f"projects/{P}/databases/(default)/documents/{path}", "fields": fields}}
    if ts_field: w["updateTransforms"] = [{"fieldPath": ts_field, "setToServerValue": "REQUEST_TIME"}]
    return req("POST", f"{BASE}:commit", uid, {"writes": [w]})

def expect(name, result, ok):
    code, body = result
    good = (code == 200) == ok
    print(("PASS" if good else "FAIL"), name, code, "" if good else body[:300])
    if not good: failures.append(name)

BLOB = "abcdefghij1234567890"
expect("profile write", write("alice", "users/alice", {"name": S("Alice"), "photoUrl": S(f"blob:{BLOB}")}, "updatedAt"), True)
expect("profile of someone else", write("alice", "users/bob", {"name": S("x"), "photoUrl": S("")}, "updatedAt"), False)
expect("blob meta", write("alice", f"blobs/{BLOB}", {"ownerUid": S("alice"), "kind": S("images"), "mime": S("image/jpeg"),
                                                 "size": I(1200000), "chunks": I(2)}, "createdAt"), True)
chunk = base64.b64encode(b"\xff" * 900000).decode()
expect("chunk 0", write("alice", f"blobs/{BLOB}/chunks/0", {"i": I(0), "data": {"bytesValue": chunk}}), True)
expect("chunk 1", write("alice", f"blobs/{BLOB}/chunks/1", {"i": I(1), "data": {"bytesValue": base64.b64encode(b'1'*300000).decode()}}), True)
expect("chunk out of range", write("alice", f"blobs/{BLOB}/chunks/2", {"i": I(2), "data": {"bytesValue": "AA=="}}), False)
expect("chunk by other user", write("bob", f"blobs/{BLOB}/chunks/1", {"i": I(1), "data": {"bytesValue": "AA=="}}), False)
expect("oversized image blob", write("alice", "blobs/zzzzzzzzzz1234567890", {"ownerUid": S("alice"), "kind": S("images"),
        "mime": S("image/jpeg"), "size": I(6 * 1024 * 1024), "chunks": I(7)}, "createdAt"), False)
expect("music blob 12MB", write("alice", "blobs/mmmmmmmmmm1234567890", {"ownerUid": S("alice"), "kind": S("music"),
        "mime": S("audio/mpeg"), "size": I(12 * 1024 * 1024), "chunks": I(14)}, "createdAt"), True)

def msg(sender, mtype, text="", media=""):
    return {"senderUid": S(sender), "senderName": S("Alice"), "senderPhoto": S(f"blob:{BLOB}"), "type": S(mtype),
            "text": S(text), "mediaUrl": S(media), "mediaPath": S(""), "refId": S(""), "durationMs": I(0)}
expect("text message", write("alice", "rooms/main/chat/m1", msg("alice", "text", "hi"), "timestamp"), True)
expect("image message", write("alice", "rooms/main/chat/m2", msg("alice", "image", media=f"blob:{BLOB}"), "timestamp"), True)
expect("spoofed sender", write("alice", "rooms/main/chat/m3", msg("bob", "text", "hi"), "timestamp"), False)
expect("edit others' message", write("bob", "rooms/main/chat/m1", msg("bob", "text", "hacked"), "timestamp"), False)
expect("song", write("alice", "rooms/main/music/s1", {"title": S("Song"), "url": S(f"blob:{BLOB}"), "storagePath": S(""),
        "uploaderUid": S("alice"), "sizeBytes": I(5000000)}, "createdAt"), True)
expect("player state", write("bob", "rooms/main/state/player", {"songId": S("s1"), "songTitle": S("Song"),
        "songUrl": S(f"blob:{BLOB}"), "playing": {"booleanValue": True}, "positionMs": I(1000), "updatedBy": S("bob")}, "updatedAt"), True)
expect("read messages", req("GET", f"{BASE}/rooms/main/chat", "carol"), True)
expect("read blob chunk", req("GET", f"{BASE}/blobs/{BLOB}/chunks/0", "carol"), True)
def anon_read():
    try:
        with urllib.request.urlopen(f"{BASE}/rooms/main/chat") as r: return r.status, ""
    except urllib.error.HTTPError as e: return e.code, e.read().decode()
expect("unauthenticated read", anon_read(), False)

def patch(uid, path, fields, mask):
    q = "&".join(f"updateMask.fieldPaths={m}" for m in mask)
    return req("PATCH", f"{BASE}/{path}?{q}&currentDocument.exists=true", uid, {"fields": fields})
expect("reply message", write("alice", "rooms/main/chat/m4", dict(msg("alice", "text", "re"),
        replyToId=S("m1"), replyToName=S("Alice"), replyToText=S("hi")), "timestamp"), True)
expect("bob reacts", patch("bob", "rooms/main/chat/m1", {"reactions": {"mapValue": {"fields": {"bob": S("❤️")}}}}, ["reactions.bob"]), True)
expect("carol reacts", patch("carol", "rooms/main/chat/m1", {"reactions": {"mapValue": {"fields": {"carol": S("😂")}}}}, ["reactions.carol"]), True)
expect("bob removes reaction", patch("bob", "rooms/main/chat/m1", {}, ["reactions.bob"]), True)
expect("react as someone else", patch("bob", "rooms/main/chat/m1", {"reactions": {"mapValue": {"fields": {"carol": S("👍")}}}}, ["reactions.carol"]), False)
expect("edit text via react", patch("bob", "rooms/main/chat/m1", {"text": S("hacked")}, ["text"]), False)
expect("author deletes own", req("DELETE", f"{BASE}/rooms/main/chat/m2", "alice"), True)
expect("delete others'", req("DELETE", f"{BASE}/rooms/main/chat/m1", "bob"), False)

def delete_all(uid, path, extra_fields=None):
    f = {"type": S("deleted"), "text": S(""), "mediaUrl": S(""), "replyToText": S(""), "reactions": {"mapValue": {}}}
    if extra_fields: f.update(extra_fields)
    return patch(uid, path, f, list(f.keys()))
expect("msg to delete", write("alice", "rooms/main/chat/d1", msg("alice", "image", media=f"blob:{BLOB}"), "timestamp"), True)
expect("bob reacts d1", patch("bob", "rooms/main/chat/d1", {"reactions": {"mapValue": {"fields": {"bob": S("😂")}}}}, ["reactions.bob"]), True)
expect("others can't delete for everyone", delete_all("bob", "rooms/main/chat/d1"), False)
expect("author can't sneak new text", delete_all("alice", "rooms/main/chat/d1", {"text": S("gotcha")}), False)
expect("author deletes for everyone", delete_all("alice", "rooms/main/chat/d1"), True)
expect("no reactions on deleted", patch("bob", "rooms/main/chat/d1", {"reactions": {"mapValue": {"fields": {"bob": S("❤️")}}}}, ["reactions.bob"]), False)
expect("old 'messages' collection closed", req("GET", f"{BASE}/rooms/main/messages", "carol"), False)

print("\nRESULT:", "ALL PASSED" if not failures else f"FAILED: {failures}")
raise SystemExit(1 if failures else 0)
