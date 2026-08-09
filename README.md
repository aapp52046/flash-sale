# 秒殺系統 · Flash Sale System

![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen)
![Redis](https://img.shields.io/badge/Redis-7-red)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Redisson](https://img.shields.io/badge/Redisson-3.27-purple)
![Docker](https://img.shields.io/badge/Docker%20Compose-ready-blue)
![CI](https://github.com/aapp52046/flash-sale/actions/workflows/ci.yml/badge.svg)
![CD](https://github.com/aapp52046/flash-sale/actions/workflows/deploy.yml/badge.svg)
![GHCR](https://img.shields.io/badge/GHCR-ghcr.io%2Faapp52046%2Fflash--sale-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

> **高併發秒殺系統 — 五層鎖策略實戰**，用真實架構解決「庫存超賣」這道經典併發難題。

```
100 庫存 · 500 併發  →  剛好 100 筆訂單 · 零超賣 · 一人一單
```

---

## 🎯 為什麼做這個專案

| 痛點 | 本專案解法 |
|---|---|
| 瞬間流量把資料庫打死 | Nginx 限流 + Redis Lua 原子扣庫存，DB 只扛「最後一擊」 |
| 多實例部署超賣 | Redisson 分佈式鎖，跨 JVM 互斥 |
| 不知該用哪種鎖 | 4 支獨立教學 API，逐層拆解 synchronized / Redisson / 樂觀鎖 / 悲觀鎖 |
| 學習→實戰落差 | 有 SPEC、架構圖、壓測腳本、即時監控儀表板 |

---

## ⚡ 系統架構

```
                ┌──────────────────────────────┐
                │       瀏覽器 / JMeter          │
                │   L0：按鈕節流 + disabled       │
                └──────────────┬───────────────┘
                               │
                ┌──────────────▼───────────────┐
                │      Nginx :80  (L0.5)        │
                │  limit_req 100req/s · 輪詢     │
                └──────┬──────────────┬─────────┘
                       │              │
              ┌────────▼───┐   ┌──────▼─────┐
              │  App :8081 │   │ App :8082  │
              │  L1 synchronized        │
              │  L2 Redisson 分佈式鎖 (共用 Redis) │
              │  L3 @Version 樂觀鎖 → L4 FOR UPDATE │
              └──────┬─────┘   └─────┬──────┘
                     │               │
        ┌────────────▼───────────────▼────────────┐
        │   Redis 7             PostgreSQL 16      │
        │   stock / lock / dedup   訂單 + version   │
        └─────────────────────────────────────────┘
```

**五層防線**：`前端節流 → Nginx 限流 → synchronized → Redis Lua → DB 樂觀/悲觀鎖 → 唯一約束`

完整時序圖見 [docs/architecture.md](docs/architecture.md)。

---

## 🛠 技術棧

| 分層 | 技術 |
|---|---|
| 語言 / 框架 | Java 21 · Spring Boot 3.2 |
| 持久層 | Spring Data JPA · Hibernate |
| 資料庫 | PostgreSQL 16 |
| 快取 / 鎖 | Redis 7 · Redisson 3.27 · Lua 腳本 |
| 安全 | Spring Security · JWT (JJWT) |
| 前端 | Thymeleaf · Bootstrap 5 |
| 部署 | Docker Compose · Nginx 負載均衡 |

---

## 🚀 快速開始

### 方式一：Docker 一鍵啟動（推薦）

```bash
cd flash-sale
docker compose up --build -d
# 開啟 http://localhost/login
```

| 帳號 | 密碼 | 角色 |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `demo` | `demo123` | USER |

登入後：Admin 預熱庫存 → `/flash` 搶購 → `/orders` 看訂單。

### 方式二：本機開發

**前置**：JDK 21、PostgreSQL 15+

Redis 已隨專案打包於 `Redis\`（Windows 版，含 `redis-server.exe`），不需另裝。

```bash
# 1. 建庫建表
createdb flash_sale
psql -U postgres -d flash_sale -f schema.sql
psql -U postgres -d flash_sale -f docker/seed.sql

# 2. 環境變數（可選，預設 postgres/postgres）
cp .env.example .env

# 3. 啟動（Windows：雙擊 start-dev.bat 會先開 Redis 再啟動 App）
start-dev.bat
# 跨平台：另開終端先啟動 Redis，再執行 ./mvnw spring-boot:run

# → http://localhost:8080/login
```

> Windows 結束時執行 `stop-dev.bat`（關 Redis）；Spring Boot 在視窗按 `Ctrl+C`。

### 即時監控儀表板

壓測時同步觀看訂單數、庫存、每秒請求、回應碼分布：

```bash
python jmeter/dashboard.py
# 開啟 http://127.0.0.1:9999
```

---

## 📊 壓測結果（真實跑過）

單實例 · 100 庫存 · 500 併發（唯一帳號每人一單）：

```
請求數  500   成功 200:100   售罄 429:400
耗時    1.6s  吞吐 306 req/s
```

| 驗證指標 | 結果 |
|---|---|
| 訂單數 `COUNT(flash_order)` | **100**（精準 = 庫存） |
| DB 庫存 | **0** |
| Redis 庫存 | **0** |
| 每人多單 | **0**（一人一單） |
| 500 錯誤 | **0** |

> 想重現？`python jmeter/stress_test.py --users 500 --stock 100`

---

## ⚙️ CI/CD

push `main` 或開 PR 即自動觸發流水線，CI 通過後自動發布 Docker 映像：

| 階段 | Workflow | 動作 |
|---|---|---|
| **CI** | `.github/workflows/ci.yml` | `./mvnw verify` — 編譯 + 單元測試（Java 21 / Temurin） |
| **CD** | `.github/workflows/deploy.yml` | CI 成功後 Buildx 建構 → 推送到 GHCR（含 gha 層快取） |

**映像位置**：`ghcr.io/aapp52046/flash-sale:latest` 與 `:git-sha`

```bash
docker pull ghcr.io/aapp52046/flash-sale:latest
```

部署門檻：CD 僅在 CI 成功後執行（`workflow_run` + `conclusion == 'success'`），壞掉就不發佈。

---

## 🔐 鎖學習路線

每個鎖機制都有一支獨立 API，可單獨觀察行為：

| Phase | API | 鎖機制 | 學習重點 |
|---|---|---|---|
| 4 | `POST /api/flash/test/local-lock` | `synchronized` + `intern()` | 僅單 JVM 有效 |
| 5 | `POST /api/flash/test/redis-lock` | Redisson `tryLock` | 跨 JVM 互斥 |
| 6 | `POST /api/flash/test/optimistic-lock` | JPA `@Version` | CAS + 重試 |
| 7 | `POST /api/flash/test/pessimistic-lock` | `SELECT FOR UPDATE` | 行鎖等待 |
| 8 | `POST /api/flash/orders` | **五層全上** | 生產級路徑 |

---

## 📁 專案結構

```
flash-sale/
├── docs/                     # 完整技術文件
│   ├── SPEC.md               # ★ 產品與技術規格書
│   ├── architecture.md       # 系統架構 + 五層鎖時序圖
│   ├── api.md                # API 規格
│   ├── database.md           # ERD + DDL
│   └── development.md        # 開發指南
├── src/main/java/com/flashsale/
│   ├── config/               # Security / Redis / Redisson / TX
│   ├── controller/           # REST + 頁面
│   ├── service/              # ★ 五層鎖實作
│   ├── repository/           # JPA（含悲觀鎖 Query）
│   ├── entity/ · dto/ · security/
│   └── common/               # 例外處理 + 枚舉
├── src/main/resources/
│   ├── scripts/deduct_stock.lua   # ★ Redis 原子扣庫存
│   └── templates/                # Thymeleaf UI
├── jmeter/                   # 壓測腳本 + 即時儀表板
├── .github/workflows/        # CI + CD（編譯測試 / GHCR 發佈）
├── docker-compose.yml        # PG + Redis + App×2 + Nginx
├── schema.sql                # 一鍵建表
└── Dockerfile
```

---

## 📚 文件索引

| 文件 | 內容 |
|---|---|
| [docs/SPEC.md](docs/SPEC.md) | 產品定位、功能/非功能需求、ADR、驗收標準 |
| [docs/architecture.md](docs/architecture.md) | 系統架構、五層鎖詳解、Mermaid 時序圖 |
| [docs/api.md](docs/api.md) | 完整 API 契約 |
| [docs/database.md](docs/database.md) | ERD、索引策略、設計決策 |
| [docs/development.md](docs/development.md) | 環境設定、開發指南 |

---

## ✨ 設計亮點

1. **縱深防禦**：前端 → 邊緣 → JVM → Redis → 樂觀 → 悲觀 → DB 唯一約束，層層遞進
2. **Lua 原子扣庫存**：單執行緒腳本，回傳碼語意明確（`-1` 未預熱 / `-2` 售罄 / `>=0` 剩餘）
3. **樂觀鎖獨立交易重試**：避免 Spring `rollback-only` 污染，衝突最多重試 3 次再降級悲觀鎖
4. **Redis 庫存用純字串**：確保 Lua `DECRBY` 相容
5. **DB 是唯一事實來源**：Redis 未預熱自動降級 DB；DB 失敗回補 Redis 庫存
6. **一人一單雙保險**：Redis dedup + DB `UNIQUE(user_id, flash_product_id)`

---

## 📜 License

[MIT](LICENSE)
