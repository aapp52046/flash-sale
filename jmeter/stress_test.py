"""Flash-sale stress test runner with live dashboard feed.

Usage:
    python jmeter/stress_test.py --base http://localhost:8080 --users 500 --stock 100
    python jmeter/stress_test.py --dashboard http://localhost:9999   # push events live

Steps:
    1. Register N unique users and save JWT tokens to build/users_tokens.txt
    2. Preheat stock via admin API
    3. Fire N concurrent POST /api/flash/orders (one per user)
    4. Print code histogram
"""

import argparse
import concurrent.futures
import json
import os
import threading
import time
import urllib.request

BASE = "http://localhost:8080"
DASH = None


def http_json(path, method="GET", token=None, body=None, timeout=30):
    req = urllib.request.Request(f"{BASE}{path}", method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data=data, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        code = getattr(e, "code", None)
        detail = ""
        try:
            detail = json.loads(e.read().decode("utf-8"))
        except Exception:
            pass
        return code, detail


def dash_post(path, payload, timeout=2):
    if not DASH:
        return
    try:
        req = urllib.request.Request(f"{DASH}{path}", data=json.dumps(payload).encode(),
                                     headers={"Content-Type": "application/json"},
                                     method="POST")
        urllib.request.urlopen(req, timeout=timeout).read()
    except Exception:
        pass


class DashFlusher:
    def __init__(self):
        self.buf = []
        self.lock = threading.Lock()
        self.stop = threading.Event()
        threading.Thread(target=self._loop, daemon=True).start()

    def add(self, ev):
        with self.lock:
            self.buf.append(ev)

    def _loop(self):
        while not self.stop.is_set():
            time.sleep(0.25)
            self._flush()
        self._flush()

    def _flush(self):
        with self.lock:
            batch, self.buf = self.buf, []
        for ev in batch:
            dash_post("/event", ev)

    def close(self):
        self.stop.set()
        self._flush()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--users", type=int, default=500)
    ap.add_argument("--stock", type=int, default=100)
    ap.add_argument("--flash-id", type=int, default=1)
    ap.add_argument("--admin", default="admin", help="admin username")
    ap.add_argument("--admin-pw", default="admin123")
    ap.add_argument("--token-file", default="build/users_tokens.txt")
    ap.add_argument("--dashboard", default="http://127.0.0.1:9999",
                    help="dashboard to push live events to (e.g. http://127.0.0.1:9999)")
    args = ap.parse_args()
    global BASE, DASH
    BASE = args.base
    DASH = args.dashboard

    dash_post("/event", {"user": "system", "code": 0, "msg": "開始壓測",
                         "ms": 0})

    # 1. register users -> tokens
    tokens = []
    t0 = time.time()
    token_dir = os.path.dirname(os.path.abspath(args.token_file))
    os.makedirs(token_dir, exist_ok=True)
    with open(args.token_file, "w", encoding="utf-8") as f:
        for i in range(1, args.users + 1):
            user = f"u{i:04d}"
            status, resp = http_json(
                "/api/auth/register",
                "POST",
                body={"username": user, "password": "123456"},
            )
            if status == 200 and resp.get("data", {}).get("token"):
                tok = resp["data"]["token"]
            else:
                status, resp = http_json(
                    "/api/auth/login",
                    "POST",
                    body={"username": user, "password": "123456"},
                )
                tok = resp.get("data", {}).get("token")
            f.write(f"{user}\t{tok}\n")
            tokens.append(tok)
    print(f"[1] registered/logged in {len(tokens)} users in {time.time()-t0:.1f}s")

    # 2. admin preheat stock
    _, resp = http_json(
        "/api/auth/login", "POST", body={"username": args.admin, "password": args.admin_pw}
    )
    admin_tok = resp.get("data", {}).get("token")
    status, preheat_body = http_json(
        f"/api/admin/flash/products/{args.flash_id}/preheat",
        "POST",
        token=admin_tok,
    )
    print(f"[2] preheat status={status} (expect 200) {preheat_body}")
    st_status, st_body = http_json(
        f"/api/admin/flash/products/{args.flash_id}/status?status=1",
        "PUT",
        token=admin_tok,
    )
    print(f"[2] open sale status={st_status} (expect 200) {st_body}")

    # 3. concurrent seckill
    body = {"flashProductId": args.flash_id, "quantity": 1}
    msg_of = {200: "搶購成功", 400: "未開放/未開始/已結束", 409: "重複下單", 429: "已售罄"}
    flusher = DashFlusher()
    pairs = [(f"u{i:04d}", tok) for i, tok in enumerate(tokens, start=1)]

    def one_order(pair):
        user, tok = pair
        t = time.time()
        status, resp = http_json("/api/flash/orders", "POST", token=tok, body=body)
        ms = int((time.time() - t) * 1000)
        code = resp.get("code", status) if isinstance(resp, dict) else status
        flusher.add({"user": user, "code": code, "msg": msg_of.get(code, "其他"),
                     "ms": ms})
        return status, resp

    print(f"[3] firing {len(tokens)} concurrent orders ...")
    t0 = time.time()
    with concurrent.futures.ThreadPoolExecutor(max_workers=200) as ex:
        results = list(ex.map(one_order, pairs))
    elapsed = time.time() - t0
    print(f"    finished in {elapsed:.1f}s  ({len(results)/elapsed:.0f} req/s)")

    # 4. histogram
    hist = {}
    for status, resp in results:
        key = resp.get("code", status) if isinstance(resp, dict) else status
        hist[key] = hist.get(key, 0) + 1
    print("[4] result histogram (http_status -> resp code):")
    for k, v in sorted(hist.items(), key=lambda x: str(x[0])):
        print(f"    code {k}: {v}")
    sample = next((resp for _, resp in results if isinstance(resp, dict) and resp.get("code") not in (200, 429, 409)), None)
    if sample:
        print(f"    sample error: {sample}")

    flusher.close()
    dash_post("/event", {"user": "system", "code": 0, "msg": "壓測結束",
                         "ms": 0})


if __name__ == "__main__":
    main()
