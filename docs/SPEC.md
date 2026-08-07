# 產品與技術規格書 (SPEC)

> **專案名稱：** 秒殺系統 (Flash Sale System)
> **版本：** 1.0.0
> **狀態：** 作品集 / Demo 就緒
> **更新日期：** 2026-08-07

---

## 1. 專案概述

### 1.1 問題陳述

電商秒殺會在單一庫存計數器上產生極端寫入競爭。過於粗糙的實作會造成：

| 失敗模式 | 影響 |
|---|---|
| 超賣 | 訂單數 > 庫存 → 退款、品牌受損 |
| 重複下單 | 同一用戶買多筆 |
| 資料庫連鎖崩潰 | 流量尖峰耗盡連線池 |
| 單機鎖無效 | 擴充到多個應用實例後就破功 |

### 1.2 目標

| 編號 | 目標 | 成功指標 |
|---|---|---|
| G1 | 併發下零超賣 | 壓測後 `訂單數 == 初始庫存` |
| G2 | 每人每 SKU 最多一單 | DB 唯一約束 + Redis 防重 |
| G3 | 多實例正確性 | 2 個 App 節點 + Nginx，仍零超賣 |
| G4 | 教學鎖機制 | 每種鎖都有獨立可觀測的 Demo API |
| G5 | 一鍵展示 | `docker compose up --build` |

### 1.3 非目標

- 金流 / 物流 / 庫存 ERP 整合
- 完整 Admin CMS 介面
- 多區域 Redis 叢集高可用（Demo 用單 Redis）
- 行動裝置原生 App

---

## 2. 角色與使用情境

| 角色 | 需求 |
|---|---|
| 消費者 | 註冊、瀏覽秒殺商品、下一筆單、查詢訂單 |
| 管理員 | 建立秒殺 SKU、預熱 Redis 庫存、開啟/關閉場次 |
| 工程師 / 學習者 | 呼叫鎖 Demo API、跑壓測、讀架構文件 |

### 主要流程

1. **認證** → 註冊 / 登入 → JWT（Header + HttpOnly Cookie）
2. **瀏覽** → 秒殺商品列表 → 含倒數計時的商品詳情
3. **秒殺** → 點擊搶購 → 多層鎖路徑 → 建立訂單
4. **管理員前置** → 建立秒殺商品 → 預熱 → 設狀態為進行中
5. **鎖實驗室** → 呼叫 `/api/flash/test/*-lock` 觀察每種機制

---

## 3. 功能需求

| 編號 | 需求 | 優先級 |
|---|---|---|
| FR-01 | 用戶註冊（BCrypt 密碼） | 必須 |
| FR-02 | JWT 登入（API Bearer + 瀏覽器 Cookie） | 必須 |
| FR-03 | 角色權限 Admin API（ADMIN） | 必須 |
| FR-04 | 秒殺商品生命週期管理 | 必須 |
| FR-05 | Redis 庫存預熱 | 必須 |
| FR-06 | 多層鎖秒殺下單 | 必須 |
| FR-07 | 每人每場次最多一單 | 必須 |
| FR-08 | 我的訂單列表 / 訂單詳情 | 必須 |
| FR-09 | 四種鎖獨立 Demo 端點 | 必須 |
| FR-10 | Thymeleaf 頁面（登入、列表、詳情、訂單） | 必須 |
| FR-11 | Nginx 秒殺路徑限流 | 應有 |
| FR-12 | Docker Compose 雙實例部署 | 必須 |

---

## 4. 非功能需求

| 編號 | 類別 | 需求 |
|---|---|---|
| NFR-01 | 正確性 | 100 庫存 / 500 併發 → 零超賣 |
| NFR-02 | 正確性 | 雙實例 + 共用 Redis/DB → 仍零超賣 |
| NFR-03 | 延遲 | 秒殺成功路徑 p50 目標 < 300ms（本機 Docker） |
| NFR-04 | 安全 | 密碼雜湊；JWT 密鑰走環境變數；git 不含密鑰 |
| NFR-05 | 可觀測性 | 每層鎖有結構化 log，方便教學/除錯 |
| NFR-06 | 可攜性 | Windows / macOS / Linux 皆可跑 |
| NFR-07 | 可維護性 | SPEC、架構、API、DB 文件在 `/docs` |

---

## 5. 系統脈絡

```
┌──────────┐    HTTP/HTTPS    ┌─────────────────────────────┐
│ 瀏覽器     │ ──────────────► │  秒殺系統                    │
│ / JMeter │                  │  (Nginx → App×N → Redis+PG) │
└──────────┘                  └─────────────────────────────┘
```

### 外部依賴

| 元件 | 角色 | 版本 |
|---|---|---|
| PostgreSQL | 庫存與訂單的唯一事實來源 | 15+ / 16 |
| Redis | 熱庫存計數、分佈式鎖、防重 | 7.x |
| Nginx | 反向代理、限流、負載均衡 | alpine |

---

## 6. 架構決策（ADR 摘要）

| ADR | 決策 | 理由 |
|---|---|---|
| ADR-01 | 多層防禦而非單一鎖 | 每層失敗方式不同，縱深防禦 |
| ADR-02 | 熱路徑庫存走 Redis Lua | 單執行緒原子性、微秒延遲 |
| ADR-03 | DB 仍是唯一事實來源 | Redis miss / crash，DB 依然一致 |
| ADR-04 | 樂觀鎖優先、悲觀鎖兜底 | 中等衝突下吞吐最高 |
| ADR-05 | 唯一約束 `(user_id, flash_product_id)` | 防重複的最後防線 |
| ADR-06 | Redis 庫存用字串值 | 相容 Lua `DECRBY` |
| ADR-07 | 樂觀鎖每次重試開新交易 | 避免版本衝突後 rollback-only 污染 |

---

## 7. 鎖策略規格

### 層級矩陣

| 層級 | 機制 | 範圍 | 失敗行為 |
|---|---|---|---|
| L0 | 按鈕禁用 + 節流 | 瀏覽器 | 僅 UX，可繞過 |
| L0.5 | `limit_req` | Nginx 每 IP | HTTP 429 |
| L1 | `synchronized` + `intern()` | 單 JVM | 跨節點失效 |
| L2a | Redisson `tryLock` | 叢集 | 等待逾時回 409 |
| L2b | Lua `DECRBY` | Redis key | -2 售罄 |
| L3 | JPA `@Version` | DB row | 重試 ≤ 3 |
| L4 | `SELECT FOR UPDATE` | DB row | 阻塞後扣減 |
| L5 | 唯一約束 | DB | 409 重複 |

### Lua 契約

| 回傳值 | 意義 |
|---|---|
| `-1` | key 不存在（未預熱）→ 降級走 DB |
| `-2` | 庫存不足（無任何變更） |
| `>= 0` | 成功；扣減後剩餘庫存 |

### 驗收情境

| 情境 | 預期 |
|---|---|
| 100 庫存、100 人各 1 次 | 100 筆訂單、庫存 0 |
| 100 庫存、500 併發 | 100 筆訂單，400 筆售罄/繁忙 |
| 同一用戶連點 | 1 筆訂單，第二次 409 |
| 雙實例共用 Redis | 仍零超賣 |
| 只用本地鎖、雙 JVM | **會超賣**（教學演示） |

---

## 8. 資料模型（摘要）

完整 ERD + DDL 見 [database.md](./database.md)。

| 資料表 | 用途 | 關鍵約束 |
|---|---|---|
| `user` | 認證 | `username` UNIQUE |
| `product` | 商品目錄 | — |
| `flash_sale_product` | 秒殺 SKU + 庫存 | `product_id` UNIQUE、`version`（L3 用） |
| `flash_order` | 訂單 | `order_no` UNIQUE、`(user_id, flash_product_id)` UNIQUE |

---

## 9. API 表面（摘要）

完整契約見 [api.md](./api.md)。

| 群組 | Base Path | 認證 |
|---|---|---|
| 認證 | `/api/auth/*` | 公開 |
| Admin 秒殺 | `/api/admin/flash/*` | ADMIN |
| 用戶秒殺 | `/api/flash/*` | USER |
| 鎖實驗室 | `/api/flash/test/*` | USER |
| 頁面 | `/login`、`/flash` 等 | 混合 |

---

## 10. 部署拓樸

### 本機開發

```
瀏覽器 → :8080 App  → localhost:5432 PG
                    → localhost:6379 Redis
```

### Docker Demo（作品集）

```
瀏覽器 → :80 Nginx ─┬→ app1:8080 ─┬→ postgres:5432
                    └→ app2:8080 ─┴→ redis:6379
```

---

## 11. 安全需求

| 編號 | 控制項 |
|---|---|
| SEC-01 | BCrypt 密碼雜湊 |
| SEC-02 | 無狀態 JWT；密鑰走環境變數 |
| SEC-03 | Admin 端點角色控管 |
| SEC-04 | git 不含憑證（`.env` 已 ignore） |
| SEC-05 | 邊緣節點對秒殺端點限流 |

---

## 12. 測試計畫

| 類型 | 方法 | 通過標準 |
|---|---|---|
| 手動 API | curl / Postman | 認證 + 鎖 Demo 回傳符合預期 |
| 併發 | JMeter / k6 | 訂單數 == 庫存 |
| 多實例 | Docker Compose 雙 App | 走 `:80` 同樣零超賣 |
| 回歸 | 重置庫存後重跑 | 結果可重現 |

### JMeter 建議設定

```text
Thread Group: 500 threads, ramp-up 1s, loop 1
Sampler: POST /api/flash/orders  { "flashProductId": 1, "quantity": 1 }
Header: Authorization: Bearer <每人一個 token>
```

---

## 13. 風險與對策

| 風險 | 對策 |
|---|---|
| Redis / DB 庫存漂移 | 開賣前預熱；DB 為唯一事實來源；DB 失敗回補 Redis |
| 長交易卡鎖 | 用短 `TransactionTemplate` 範圍；Redis 操作在交易外 |
| 開始/結束時間偏差 | 管理員控制 status 旗標 + 時間檢查 |
| 專案內預設 JWT 密鑰 | 文件說明覆寫方式；Docker 用環境變數 |

---

## 14. 交付清單（作品集）

- [x] 可運行的 Spring Boot 3.2 / Java 21 應用
- [x] 完整鎖實作（非 stub）
- [x] Docker Compose 全棧
- [x] 本規格書 (SPEC)
- [x] 架構圖
- [x] API / DB / 開發文件
- [x] README 快速開始
- [x] `.gitignore` + env 範例 + MIT License
- [x] 壓測腳本 + 即時監控儀表板
