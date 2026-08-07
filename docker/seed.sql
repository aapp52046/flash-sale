-- Demo seed data (runs only on first Postgres container init)

INSERT INTO product (name, description, normal_price, total_stock)
SELECT '限量潮T', 'Flash Sale demo product — limited tee', 100.00, 100
WHERE NOT EXISTS (SELECT 1 FROM product WHERE name = '限量潮T');

INSERT INTO product (name, description, normal_price, total_stock)
SELECT '無線耳機', 'Flash Sale demo product — earbuds', 2990.00, 50
WHERE NOT EXISTS (SELECT 1 FROM product WHERE name = '無線耳機');

INSERT INTO flash_sale_product (product_id, flash_price, flash_stock, start_time, end_time, status)
SELECT p.id, 9.99, 100,
       NOW() - INTERVAL '1 minute',
       NOW() + INTERVAL '24 hour',
       1
FROM product p
WHERE p.name = '限量潮T'
  AND NOT EXISTS (SELECT 1 FROM flash_sale_product f WHERE f.product_id = p.id);

-- admin / admin123
INSERT INTO "user" (username, password, role)
SELECT 'admin',
       '$2a$10$2FnNHWvXSfgbQJ0XsNg3Z.dtc6iv.1MFeJi8UAzLveBh5u4a7baeu',
       'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM "user" WHERE username = 'admin');

-- demo / demo123
INSERT INTO "user" (username, password, role)
SELECT 'demo',
       '$2a$10$.fpv/WWdw02S6gfrOQV8Ve5xdF5nPdmbYer3HBICjmzt3JeMQkyr2',
       'USER'
WHERE NOT EXISTS (SELECT 1 FROM "user" WHERE username = 'demo');
