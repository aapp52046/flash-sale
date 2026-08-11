"""Real-time flash-sale dashboard.

Starts a local web dashboard (default http://localhost:9999) that:
  - polls DB (orders / stock) and Redis (stock / dedup) every second
  - collects live request events pushed by jmeter/stress_test.py
  - can spawn / stop the stress test from the UI

Usage:
    python jmeter/dashboard.py
    # open http://localhost:9999
"""

import collections
import json
import os
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PSQL = os.environ.get("DASH_PSQL", r"C:\Program Files\PostgreSQL\18\bin\psql.exe")
DB_PASSWORD = os.environ.get("DASH_DB_PASSWORD", "postgres")
DB_NAME = os.environ.get("DASH_DB_NAME", "flash_sale")
REDIS_CLI = os.environ.get("DASH_REDIS_CLI", os.path.join(os.path.dirname(BASE_DIR), "Redis", "redis-cli.exe"))
FLASH_ID = int(os.environ.get("DASH_FLASH_ID", "1"))
TARGET_STOCK = int(os.environ.get("DASH_STOCK", "100"))
PORT = int(os.environ.get("DASH_PORT", "9999"))
APP_URL = os.environ.get("DASH_APP_URL", "http://localhost:8080")

state = {
    "orders": 0,
    "db_stock": 0,
    "redis_stock": -1,
    "dedup": 0,
    "app_alive": False,
    "running": False,
    "start_ts": None,
    "codes": collections.Counter(),
    "latencies": collections.deque(maxlen=5000),
    "events": collections.deque(maxlen=3000),
    "series": collections.deque(maxlen=3000),  # (elapsed_s, cum_ok, cum_reqs)
    "timeline": collections.deque(maxlen=900),   # (elapsed_s, orders, reqs)
    "log": collections.deque(maxlen=200),
    "last_req_count": 0,
    "req_per_sec": 0.0,
}
lock = threading.Lock()
proc = {"handle": None}

REQ_CODES = (200, 409, 429, 500)


def req_total():
    return sum(state["codes"].get(c, 0) for c in REQ_CODES)


def psql_one(sql):
    try:
        out = subprocess.run(
            [PSQL, "-U", "postgres", "-h", "localhost", "-d", DB_NAME, "-t", "-A", "-c", sql],
            capture_output=True, text=True, timeout=5,
            env={**os.environ, "PGPASSWORD": DB_PASSWORD},
        )
        return out.stdout.strip()
    except Exception:
        return None


def redis_one(args):
    try:
        out = subprocess.run([REDIS_CLI] + args, capture_output=True, text=True, timeout=5)
        return out.stdout.strip()
    except Exception:
        return None


def count_dedup_keys():
    try:
        out = subprocess.run([REDIS_CLI, "KEYS", "flash:dedup:*"],
                             capture_output=True, text=True, timeout=5)
        n = out.stdout.strip()
        return len(n.splitlines()) if n else 0
    except Exception:
        return 0


def reset_state(stock=None):
    stock = stock or TARGET_STOCK
    sql = (f"TRUNCATE flash_order RESTART IDENTITY; "
           f"UPDATE flash_sale_product SET flash_stock={stock}, "
           f"start_time=NOW()-INTERVAL '1 minute', "
           f"end_time=NOW()+INTERVAL '24 hour' WHERE id={FLASH_ID};")
    try:
        subprocess.run([PSQL, "-U", "postgres", "-h", "localhost", "-d", DB_NAME, "-c", sql],
                       capture_output=True, text=True, timeout=10,
                       env={**os.environ, "PGPASSWORD": DB_PASSWORD})
    except Exception:
        pass
    redis_one(["FLUSHDB"])
    with lock:
        state["orders"] = 0
        state["db_stock"] = stock
        state["redis_stock"] = stock
        state["dedup"] = 0
        state["codes"].clear()
        state["latencies"].clear()
        state["events"].clear()
        state["series"].clear()
        state["timeline"].clear()
        state["last_req_count"] = 0
        state["log"].appendleft(f"[dashboard] 已重置：庫存={stock}")
    return stock


def app_alive():
    try:
        import socket
        with socket.create_connection(("localhost", 8080), timeout=2):
            return True
    except Exception:
        return False


def monitor_loop():
    while True:
        # slow calls WITHOUT holding the lock
        orders_s = psql_one(f"SELECT COUNT(*) FROM flash_order WHERE flash_product_id={FLASH_ID};")
        db_stock_s = psql_one(f"SELECT flash_stock FROM flash_sale_product WHERE id={FLASH_ID};")
        rstock_s = redis_one(["GET", f"flash:stock:{FLASH_ID}"])
        dedup_n = count_dedup_keys()
        alive = app_alive()
        now = time.time()

        with lock:
            state["orders"] = int(orders_s or 0)
            state["db_stock"] = int(db_stock_s) if db_stock_s else -1
            state["redis_stock"] = int(rstock_s) if rstock_s else -1
            state["dedup"] = dedup_n
            state["app_alive"] = alive
            elapsed = now - state["start_ts"] if state["start_ts"] else 0.0
            total = req_total()
            state["timeline"].append((round(elapsed, 1), state["orders"], total - state["last_req_count"]))
            state["req_per_sec"] = total - state["last_req_count"]
            state["last_req_count"] = total
        time.sleep(1)


def spawn_stress(users, stock):
    if proc["handle"] and proc["handle"].poll() is None:
        return "already running"
    env = dict(os.environ)
    cmd = ["python", os.path.join(BASE_DIR, "stress_test.py"),
           "--users", str(users), "--stock", str(stock),
           "--dashboard", f"http://127.0.0.1:{PORT}"]
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                         text=True, bufsize=1, env=env,
                         cwd=os.path.dirname(BASE_DIR))
    proc["handle"] = p
    with lock:
        state["running"] = True
        state["start_ts"] = time.time()
        state["codes"].clear()
        state["latencies"].clear()
        state["events"].clear()
        state["series"].clear()
        state["timeline"].clear()
        state["last_req_count"] = 0

    def reader():
        for line in p.stdout:
            line = line.rstrip()
            if line:
                with lock:
                    state["log"].appendleft(line)
        with lock:
            state["running"] = False
            state["log"].appendleft("[dashboard] stress test finished")

    threading.Thread(target=reader, daemon=True).start()
    return "started"


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body, ctype="application/json; charset=utf-8"):
        data = body if isinstance(body, bytes) else json.dumps(body, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path in ("/", "/index.html"):
            with open(os.path.join(BASE_DIR, "dashboard.html"), "rb") as f:
                self._send(200, f.read(), "text/html; charset=utf-8")
            return
        if self.path == "/api/state":
            with lock:
                lats = list(state["latencies"])
                sorted_lats = sorted(lats)
                body = {
                    "orders": state["orders"],
                    "target": TARGET_STOCK,
                    "db_stock": state["db_stock"],
                    "redis_stock": state["redis_stock"],
                    "dedup": state["dedup"],
                    "app_alive": state["app_alive"],
                    "running": state["running"],
                    "start_ts": state["start_ts"],
                    "codes": dict(state["codes"]),
                    "req_per_sec": round(state["req_per_sec"], 1),
                    "events": list(state["events"]),
                    "series": list(state["series"]),
                    "timeline": list(state["timeline"]),
                    "log": list(state["log"]),
                    "avg_ms": round(sum(lats) / len(lats), 1) if lats else 0,
                    "p50_ms": sorted_lats[int(len(sorted_lats) * 0.50)] if sorted_lats else 0,
                    "p99_ms": sorted_lats[int(len(sorted_lats) * 0.99)] if sorted_lats else 0,
                }
            self._send(200, body)
            return
        self._send(404, {"error": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0) or 0)
        payload = {}
        if length:
            try:
                payload = json.loads(self.rfile.read(length).decode("utf-8"))
            except Exception:
                pass

        if self.path == "/event":
            with lock:
                code = payload.get("code")
                ms = payload.get("ms") or 0
                state["codes"][code] += 1
                if isinstance(ms, (int, float)):
                    state["latencies"].append(ms)
                state["events"].appendleft({
                    "t": time.strftime("%H:%M:%S"),
                    "user": payload.get("user", "-"),
                    "code": code,
                    "msg": payload.get("msg", ""),
                    "ms": ms,
                })
                if state["start_ts"] and code in REQ_CODES:
                    elapsed = round(time.time() - state["start_ts"], 2)
                    state["series"].append((elapsed, state["codes"].get(200, 0), req_total()))
            self._send(200, {"ok": True})
            return

        if self.path == "/start":
            users = int(payload.get("users", 500))
            stock = int(payload.get("stock", TARGET_STOCK))
            reset_state(stock)
            result = spawn_stress(users, stock)
            self._send(200, {"ok": True, "result": result})
            return

        if self.path == "/reset":
            stock = int(payload.get("stock", TARGET_STOCK))
            reset_state(stock)
            self._send(200, {"ok": True, "stock": stock})
            return

        if self.path == "/stop":
            if proc["handle"] and proc["handle"].poll() is None:
                proc["handle"].terminate()
                with lock:
                    state["running"] = False
                    state["log"].appendleft("[dashboard] stress test stopped")
            self._send(200, {"ok": True})
            return

        self._send(404, {"error": "not found"})

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    threading.Thread(target=monitor_loop, daemon=True).start()
    srv = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"Dashboard: http://127.0.0.1:{PORT}")
    print(f"App check : {APP_URL}")
    print("Press Ctrl+C to stop")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
