# API 規格

## Base URL

```
開發環境: http://localhost:8080
Docker:   http://localhost:80 (Nginx)
```

## 認證方式

登入後 JWT 雙通道傳遞：
- **API 呼叫**：`Authorization: Bearer <token>` header
- **頁面請求**：`jwt` HttpOnly Cookie (自動附帶)

---

## 1. 認證 API

### 註冊

```
POST /api/auth/register
```

**Request Body:**
```json
{
  "username": "testuser",
  "password": "123456"
}
```

**Response 200:**
```json
{
  "code": 200,
  "message": "註冊成功",
  "data": {
    "token": "eyJhbGciOi...",
    "username": "testuser"
  }
}
```

**Response 400:**
```json
{
  "code": 400,
  "message": "帳號已存在"
}
```

---

### 登入

```
POST /api/auth/login
```

**Request Body:**
```json
{
  "username": "testuser",
  "password": "123456"
}
```

**Response 200:**
```json
{
  "code": 200,
  "message": "登入成功",
  "data": {
    "token": "eyJhbGciOi...",
    "username": "testuser"
  }
}
```

---

## 2. 管理端 API (需 ADMIN 角色)

### 建立秒殺商品

```
POST /api/admin/flash/products
Role: ADMIN
```

```json
{
  "productId": 1,
  "flashPrice": 9.99,
  "flashStock": 100,
  "startTime": "2026-06-25T10:00:00",
  "endTime": "2026-06-25T12:00:00"
}
```

### 預熱庫存到 Redis

```
POST /api/admin/flash/products/{id}/preheat
```

秒殺開始前執行，將 DB 庫存同步到 `flash:stock:{id}`。

### 更新秒殺狀態

```
PUT /api/admin/flash/products/{id}/status?status=1
```

| status | 意義 |
|---|---|
| 0 | 未開始 |
| 1 | 進行中 |
| 2 | 已結束 |

---

## 3. 用戶端 API

### 進行中秒殺商品列表

```
GET /api/flash/products
Auth: Bearer <token>
```

### 秒殺商品詳情

```
GET /api/flash/products/{id}
Auth: Bearer <token>
```

---

## 🔒 4. 鎖學習 API (★ 核心)

> 每個 Phase 一支獨立 API，互不干擾。統一使用 `productId` 作為鎖目標。

### Phase 4 — 本地鎖

```
POST /api/flash/test/local-lock?productId=1
Auth: Bearer <token>
```

**Response:**
```json
{
  "code": 200,
  "data": {
    "thread": "http-nio-8080-exec-3",
    "before": 100,
    "after": 99,
    "success": true,
    "lock": "synchronized"
  }
}
```

---

### Phase 5 — Redis 分佈式鎖

```
POST /api/flash/test/redis-lock?productId=1
Auth: Bearer <token>
```

**Response (成功):**
```json
{
  "code": 200,
  "data": {
    "thread": "http-nio-8080-exec-5",
    "before": 100,
    "after": 99,
    "success": true,
    "lock": "Redisson"
  }
}
```

**Response (鎖競爭失敗):**
```json
{
  "code": 200,
  "data": {
    "thread": "http-nio-8080-exec-7",
    "success": false,
    "message": "鎖競爭失敗",
    "lock": "Redisson"
  }
}
```

---

### Phase 6 — 樂觀鎖

```
POST /api/flash/test/optimistic-lock?productId=1
Auth: Bearer <token>
```

**Response (一次成功):**
```json
{
  "code": 200,
  "data": {
    "thread": "http-nio-8080-exec-2",
    "attempts": 1,
    "before": 100,
    "after": 99,
    "success": true,
    "lock": "@Version"
  }
}
```

**Response (重試後成功):**
```json
{
  "code": 200,
  "data": {
    "thread": "http-nio-8080-exec-3",
    "attempts": 2,
    "before": 99,
    "after": 98,
    "success": true,
    "lock": "@Version"
  }
}
```

**Response (3次全失敗):**
```json
{
  "code": 200,
  "data": {
    "thread": "http-nio-8080-exec-4",
    "attempts": 3,
    "success": false,
    "message": "樂觀鎖重試3次全失敗",
    "lock": "@Version"
  }
}
```

---

### Phase 7 — 悲觀鎖

```
POST /api/flash/test/pessimistic-lock?productId=1
Auth: Bearer <token>
```

**Response (無競爭):**
```json
{
  "code": 200,
  "data": {
    "thread": "http-nio-8080-exec-1",
    "waitMs": 5,
    "before": 100,
    "after": 99,
    "success": true,
    "lock": "PESSIMISTIC_WRITE"
  }
}
```

**Response (被阻塞等待):**
```json
{
  "code": 200,
  "data": {
    "thread": "http-nio-8080-exec-2",
    "waitMs": 342,
    "before": 99,
    "after": 98,
    "success": true,
    "lock": "PESSIMISTIC_WRITE"
  }
}
```

> `waitMs` 可觀察到被前一個交易阻塞的等待時間。

---

### Phase 8 — 實戰秒殺 (五層全上)

```
POST /api/flash/orders
Auth: Bearer <token>
```

**Request:**
```json
{
  "flashProductId": 1,
  "quantity": 1
}
```

**成功:**
```json
{
  "code": 200,
  "message": "搶購成功",
  "data": {
    "orderNo": "FS1719123456789A3F2",
    "flashProductId": 1,
    "amount": 9.99
  }
}
```

**售罄:**
```json
{
  "code": 429,
  "message": "已售罄"
}
```

**重複下單:**
```json
{
  "code": 409,
  "message": "已參與過本場秒殺"
}
```

**請求衝突:**
```json
{
  "code": 409,
  "message": "請求處理中，請勿重複提交"
}
```

---

### 我的訂單

```
GET /api/flash/orders/my
Auth: Bearer <token>
```

### 訂單詳情

```
GET /api/flash/orders/{orderNo}
Auth: Bearer <token>
```

---

## 錯誤碼總覽

| Code | 意義 | 情境 |
|---|---|---|
| 200 | 成功 | |
| 400 | 請求參數錯誤 | 驗證失敗、欄位缺失 |
| 401 | 未認證 | 未登入、Token 過期 |
| 404 | 不存在 | 商品/訂單不存在 |
| 409 | 衝突 | 重複下單、鎖競爭失敗 |
| 429 | 資源耗盡 | 庫存售罄、限流觸發 |
| 500 | 伺服器錯誤 | 未預期例外 |
