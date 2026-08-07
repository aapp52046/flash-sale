# 開發指南

## 環境準備

### 必備軟體

| 軟體 | 用途 | 下載 |
|---|---|---|
| JDK 21 | Java 執行環境 | `winget install EclipseAdoptium.Temurin.21.JDK` |
| Maven | 建置工具 | `winget install Apache.Maven.3` 或用 IDE 自帶 |
| PostgreSQL 15+ | 資料庫 | https://www.postgresql.org/download/windows/ |
| Redis 7 | 快取/鎖 | Docker `redis:7-alpine` 或 Windows build |
| Docker Desktop | 一鍵全棧 | 建議作品集 demo 使用 |
| IntelliJ IDEA / Eclipse | IDE | |

### 最快路徑：Docker

```bash
docker compose up --build -d
# http://localhost/login
# admin/admin123 · demo/demo123
```

### 本機開發步驟

```powershell
# 1. PostgreSQL
#    CREATE DATABASE flash_sale;
#    psql -U postgres -d flash_sale -f schema.sql
#    psql -U postgres -d flash_sale -f docker/seed.sql

# 2. Redis（本機 6379）

# 3. 環境變數（可選）
copy .env.example .env
# 預設 DB: postgres/postgres

# 4. 啟動
mvn spring-boot:run
# http://localhost:8080/login
```

### 連線設定（可用環境變數覆寫）

| 項目 | 預設值 | 環境變數 |
|---|---|---|
| JDBC URL | `jdbc:postgresql://localhost:5432/flash_sale` | `DB_URL` |
| Username | `postgres` | `DB_USERNAME` |
| Password | `postgres` | `DB_PASSWORD` |
| Redis host | `localhost` | `REDIS_HOST` |
| JWT secret | dev default | `JWT_SECRET` |

### Docker 雙實例驗證

```bash
docker compose up --build -d
# Nginx LB: http://localhost
# 證明 Redisson 跨 JVM 有效（僅 synchronized 會超賣）
```

---

## 專案結構

```
flash-sale/
├── README.md · LICENSE · docs/SPEC.md
├── docs/                          # SPEC / architecture / api / database / development
├── schema.sql · docker/seed.sql
├── docker-compose.yml · Dockerfile · nginx/
├── 鎖API參照/                     # 鎖實作學習對照（非編譯路徑）
├── src/main/java/com/flashsale/
│   ├── config/                    # Security, Redis, Redisson, TX
│   ├── entity/ · repository/ · dto/
│   ├── security/                  # JWT
│   ├── service/                   # ★ Lock demos + FlashSaleService
│   └── controller/
└── src/main/resources/
    ├── application*.yml           # 密碼請用環境變數
    ├── scripts/deduct_stock.lua
    └── templates/
```

---

## Phase 開發順序

### Phase 1 — 專案骨架 ✅
- pom.xml (Java 21 + PostgreSQL driver), application.yml
- 在 PostgreSQL 執行 schema.sql 建表

### Phase 2 — 用戶認證 ✅
- `UserService` (BCrypt 註冊/登入)
- `AuthController` (JWT 產生 + Cookie 設定)
- `JwtUtil` + `JwtAuthFilter` + `SecurityConfig`

### Phase 3 — 商品管理 + 前端 ✅
- `Product`, `FlashSaleProduct`, `FlashOrder` Entity
- `AdminFlashController` (建立秒殺商品)
- `PageController` + 5 個 Thymeleaf 模板
- 前端 Layer 0 倒數計時 + 節流

### 鎖 API（Phase 4–8）— 已實作完成

`src/` 內 Service 為完整可運行實作；`鎖API參照/` 保留為學習對照筆記。

| Phase | API | Service |
|---|---|---|
| 4 | `POST /api/flash/test/local-lock` | `LocalLockDemoService` |
| 5 | `POST /api/flash/test/redis-lock` | `RedisLockDemoService` |
| 6 | `POST /api/flash/test/optimistic-lock` | `OptimisticLockDemoService` |
| 7 | `POST /api/flash/test/pessimistic-lock` | `PessimisticLockDemoService` |
| 8 | `POST /api/flash/orders` | `FlashSaleService`（五層全上） |
| 9 | Docker Compose | 雙 App + Nginx 驗證跨 JVM |

**驗收（Phase 8/9）：** 100 庫存 / 500 併發 → 訂單=100、庫存=0、零超賣

---

## 測試用種子資料

```sql
-- 建立測試商品
INSERT INTO product (name, description, normal_price, total_stock)
VALUES ('限量潮T', '秒殺測試商品', 100.00, 100);

-- 建立秒殺場次 (開始時間設為過去，立刻生效)
INSERT INTO flash_sale_product (product_id, flash_price, flash_stock,
                                 start_time, end_time, status)
VALUES (1, 9.99, 100,
        NOW() - INTERVAL '1 minute',
        NOW() + INTERVAL '1 hour',
        1);  -- status=1 進行中

-- 預熱庫存到 Redis 或手動 POST /api/admin/flash/products/1/preheat
```

## 手動測試清單

```powershell
# 註冊
curl -X POST http://localhost:8080/api/auth/register `
  -H "Content-Type: application/json" `
  -d "{\"username\":\"test\",\"password\":\"123456\"}"

# 登入 (取得 token)
curl -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"username\":\"test\",\"password\":\"123456\"}"

# 查看秒殺商品
curl http://localhost:8080/api/flash/products `
  -H "Authorization: Bearer <token>"

# Phase 4: 本地鎖測試
curl -X POST "http://localhost:8080/api/flash/test/local-lock?productId=1" `
  -H "Authorization: Bearer <token>"

# Phase 8: 實戰秒殺
curl -X POST http://localhost:8080/api/flash/orders `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer <token>" `
  -d "{\"flashProductId\":1,\"quantity\":1}"
```

## Redis Windows 小技巧

```powershell
# 查看庫存
redis-cli GET flash:stock:1

# 查看已下單標記
redis-cli KEYS "flash:dedup:*"

# 手動清空 (重設測試)
redis-cli FLUSHDB
```

## PostgreSQL 小技巧

```sql
-- 查庫存
SELECT id, flash_stock, version FROM flash_sale_product;

-- 查訂單數
SELECT COUNT(*) FROM flash_order WHERE flash_product_id = 1;

-- 查悲觀鎖等待（Phase 7 進階）
SELECT * FROM pg_locks WHERE NOT granted;
SELECT pid, state, query FROM pg_stat_activity WHERE wait_event_type = 'Lock';
```
