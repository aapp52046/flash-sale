# 壓測筆記

## 目標

```text
庫存 = 100
併發買家 >= 500（每人唯一 JWT）
驗收：COUNT(flash_order) = 100、flash_stock = 0、零超賣
```

## 事前準備

1. 預熱：`POST /api/admin/flash/products/{id}/preheat`（用 admin token）
2. 確認 `status = 1` 且時間窗已開啟
3. 預先註冊 N 個使用者（或準備 token CSV）

## JMeter 大綱

| 元件 | 設定 |
|---|---|
| Thread Group | 500 threads、ramp-up 1s、loop 1 |
| HTTP Request | `POST /api/flash/orders` |
| Body | `{"flashProductId":1,"quantity":1}` |
| Header | `Authorization: Bearer ${token}` |
| CSV Data Set | 每行一個 token |

## 雙實例

`docker compose up` 後，JMeter 指向 `http://localhost`（Nginx）。

## SQL 驗證

```sql
SELECT COUNT(*) FROM flash_order WHERE flash_product_id = 1;
SELECT flash_stock, version FROM flash_sale_product WHERE id = 1;
SELECT user_id, COUNT(*) FROM flash_order GROUP BY user_id HAVING COUNT(*) > 1;
```
