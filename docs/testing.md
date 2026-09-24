# Kiểm thử

## Lệnh hiện có

```powershell
cd Sass-Pos-Application
.\mvnw.cmd test

cd ..\sass-pos-web
npm run lint
npx tsc --noEmit
npm test
npm run build
```

Backend test profile dùng H2 in-memory và có secret test riêng; Flyway tắt trong profile này. Unit test hiện kiểm tra tenant branch/inventory (bao gồm các role tenant), role/employee, sales report aggregation, inventory-movement report aggregation/scope, audit-log authorization, customer history scope/refund calculation và Spring context. `MysqlInventoryConcurrencyIT` dùng Testcontainers MySQL 8.4 + Flyway để chứng minh lock chống oversell; nó tự skip khi Docker daemon chưa chạy. Frontend Vitest kiểm tra permission helper, BFF API error normalization, locale switcher và audit-log scope helper.

## Chiến lược tiếp theo

Các bài test sau vẫn cần bổ sung trước khi release production:

1. Mở rộng tenant/security tests cho mọi controller và mọi role, không chỉ inventory/branch/report.
2. Mở rộng concurrency từ inventory adjustment sang create-order/refund ở mức HTTP/API thực.
3. Chạy xác nhận Testcontainers khi Docker Desktop/CI Docker sẵn sàng (máy hiện tại sẽ skip an toàn nếu daemon tắt).
4. Shift lifecycle, refund remaining quantity và inventory movement integration test.
5. Playwright E2E với backend/MySQL thật: login → catalog/inventory → open shift → POS → refund → close shift.

Không xem test render đơn giản là E2E nghiệp vụ. Khi test POST order, luôn gửi cùng `Idempotency-Key` trong retry scenario.
