-- Atomic stock deduction for flash sale
-- KEYS[1] = flash:stock:{productId}
-- ARGV[1] = quantity to deduct
-- Returns:
--   -1  key missing (not preheated)
--   -2  insufficient stock (no change)
--  >=0  success; value is remaining stock after DECRBY

local key = KEYS[1]
local qty = tonumber(ARGV[1])
local stock = redis.call('GET', key)
if not stock then
    return -1
end
stock = tonumber(stock)
if stock < qty then
    return -2
end
return redis.call('DECRBY', key, qty)
