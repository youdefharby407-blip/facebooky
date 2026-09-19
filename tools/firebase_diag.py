"""Checks the Firebase project the same way the app does (anonymous auth -> Firestore -> Storage)."""
import json, urllib.request, urllib.error, urllib.parse

gs = json.load(open("app/google-services.json"))
KEY = gs["client"][0]["api_key"][0]["current_key"]
PROJECT = gs["project_info"]["project_id"]
BUCKET = gs["project_info"].get("storage_bucket", "")
HDR = {"X-Android-Package": "com.yousef.facebooky"}

def call(name, method, url, body=None, headers=None, raw=None):
    h = dict(HDR); h.update(headers or {})
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    if data is not None and "Content-Type" not in h: h["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            txt = r.read().decode(errors="replace"); code = r.status
    except urllib.error.HTTPError as e:
        txt = e.read().decode(errors="replace"); code = e.code
    except Exception as e:
        txt = repr(e); code = -1
    print(f"### {name}: HTTP {code}\n{txt[:1500]}\n")
    try: return code, json.loads(txt)
    except Exception: return code, {}

print("project:", PROJECT, "bucket:", BUCKET)
code, j = call("auth signUp (anonymous)", "POST",
               f"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={KEY}", {"returnSecureToken": True})
tok, uid = j.get("idToken"), j.get("localId")
if not tok: raise SystemExit("auth failed")
A = {"Authorization": f"Bearer {tok}"}
base = f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/(default)/documents"
call("firestore read messages", "GET", f"{base}/rooms/main/messages?pageSize=1", headers=A)
call("firestore list databases (may 403, ok)", "GET",
     f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases", headers=A)
commit = {"writes": [{
    "update": {"name": f"projects/{PROJECT}/databases/(default)/documents/users/{uid}",
               "fields": {"name": {"stringValue": "diag"}, "photoUrl": {"stringValue": ""}}},
    "updateTransforms": [{"fieldPath": "updatedAt", "setToServerValue": "REQUEST_TIME"}]}]}
call("firestore write users/{uid}", "POST",
     f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/(default)/documents:commit", commit, A)
call("firestore delete users/{uid}", "DELETE", f"{base}/users/{uid}", headers=A)
obj = urllib.parse.quote(f"media/{uid}/profile/diag.jpg", safe="")
FA = {"Authorization": f"Firebase {tok}"}
call("storage upload", "POST", f"https://firebasestorage.googleapis.com/v0/b/{BUCKET}/o?name={obj}",
     headers=dict(FA, **{"Content-Type": "image/jpeg"}), raw=b"\xff\xd8\xff\xe0diag")
call("storage delete", "DELETE", f"https://firebasestorage.googleapis.com/v0/b/{BUCKET}/o/{obj}", headers=FA)
