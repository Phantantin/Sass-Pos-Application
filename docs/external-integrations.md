# Xác minh tích hợp Stripe và UploadThing

Tài liệu này là checklist vận hành. Không đặt key/token vào source, migration, log hay ticket. Nếu credential đã từng được dán vào chat, terminal hoặc Git, hãy rotate nó trước khi dùng.

## Stripe Billing

Code backend đã tạo Checkout subscription, Customer Portal và chỉ xử lý webhook sau khi xác thực chữ ký. Stripe vẫn tắt mặc định để local development không gọi dịch vụ ngoài.

1. Trong **Stripe test mode**, tạo hai recurring Price (BASIC/PRO), bật Customer Portal và cấu hình policy đổi/hủy gói.
2. Đặt các biến chỉ trong secret store hoặc `.env` local không commit: `STRIPE_ENABLED=true`, `STRIPE_SECRET_KEY`, `STRIPE_BASIC_PRICE_ID`, `STRIPE_PRO_PRICE_ID`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_APP_URL`.
3. Chạy backend, sau đó dùng Stripe CLI để chuyển tiếp webhook local:

```powershell
stripe listen --forward-to http://localhost:5000/api/subscriptions/stripe/webhook
```

Sao chép signing secret mà lệnh này cấp vào `STRIPE_WEBHOOK_SECRET`; nó khác với API key và không được chia sẻ.

4. Đăng nhập bằng `ROLE_STORE_ADMIN`, mở trang subscription, thử Checkout với recurring BASIC/PRO bằng thẻ test Stripe, sau đó kiểm tra `GET /api/subscriptions/current`. Redirect `?checkout=success` không phải xác nhận thanh toán; chỉ webhook đã ký mới được cập nhật plan/status.
5. Mở Customer Portal, đổi/hủy gói và xác nhận các event `checkout.session.completed`, `customer.subscription.created`, `customer.subscription.updated`, `customer.subscription.deleted` đều tới được backend, có HTTP 200, và đồng bộ đúng subscription của store.
6. Trước production, tạo lại Price/live webhook endpoint riêng; không dùng test key, test Price ID hoặc signing secret local trong production.

## UploadThing

Route upload `productImage` chỉ nhận ảnh tối đa 4 MB, kiểm tra cookie phiên qua backend và chỉ cho `ROLE_ADMIN`, `ROLE_STORE_ADMIN`, `ROLE_STORE_MANAGER` tải ảnh.

1. Rotate token cũ, sau đó lấy **V7 token** mới từ UploadThing Dashboard và chỉ đặt token đó vào `sass-pos-web/.env.local`:

```env
UPLOADTHING_TOKEN=token_moi_tu_UploadThing
```

2. Không đặt tiền tố `NEXT_PUBLIC_`, không commit `.env.local`, restart Next.js sau khi thay đổi environment.
3. Đăng nhập bằng một role được cấp quyền, mở Products, chọn PNG/JPG/WEBP nhỏ hơn 4 MB, lưu sản phẩm và xác nhận URL ảnh cùng thumbnail được lưu. Thử một role không có quyền để xác nhận upload bị từ chối.

Với Docker Desktop đang chạy, `.\scripts\run-e2e.ps1 -VerifyUploadThing` chạy cùng kiểm thử MySQL cô lập và xác nhận một upload UI thật. Export token tạm thời trong PowerShell cho lệnh này; không commit token vào Compose hay source. SDK v7 không nhận `UPLOADTHING_SECRET` và `UPLOADTHING_APP_ID` riêng lẻ.

## Kiểm thử MySQL/Testcontainers

`mvn test` hiện bao gồm bài `MysqlInventoryConcurrencyIT`. Khi Docker đang chạy, test tạo MySQL 8.4 tạm thời, áp dụng Flyway và chạy hai giao dịch bán cùng đơn vị tồn cuối. Một giao dịch phải thành công, giao dịch còn lại nhận conflict; không được oversell. Khi Docker daemon không sẵn sàng, Testcontainers đánh dấu bài này skipped để các unit test khác vẫn chạy.
