# Tích hợp API

## Quy ước request

API backend chạy tại `http://localhost:5000`. Các endpoint dưới `/api/**` yêu cầu:

```http
Authorization: Bearer <jwt>
Accept-Language: vi
Content-Type: application/json
```

Web frontend không gọi API protected từ browser trực tiếp. Nó gọi `/api/backend/...` của Next.js; BFF đọc cookie HttpOnly, thêm Bearer token và chuyển `Accept-Language`/`Idempotency-Key` sang backend.

## Nhóm endpoint chính

| Nhóm                  | Ví dụ                                                                               | Ghi chú                                                                                          |
| --------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Auth                  | `POST /auth/signup`, `POST /auth/login`                                             | signup ép role store admin ở server                                                              |
| Profile               | `GET /api/users/profile`                                                            | dùng để render scope/quyền frontend                                                              |
| Store/branch/employee | `/api/stores`, `/api/branches`, `/api/employees`                                    | server kiểm tra store/branch ownership                                                           |
| Catalog               | `/api/categories`, `/api/products`                                                  | category và product phải cùng store                                                              |
| Inventory             | `/api/inventories/branch/{branchId}`                                                | write cần permission và movement audit                                                           |
| Customer              | `/api/customers`, `/api/customers/{id}/history`                                     | customer scope theo store; history phân trang và branch-scope khi áp dụng                        |
| POS                   | `POST /api/orders`                                                                  | cần `Idempotency-Key`; giá/tổng do server tính                                                   |
| Refund/shift          | `/api/refunds`, `/api/shift-reports`                                                | refund cần active shift và amount không tin client                                               |
| Dashboard/report      | `/api/dashboard/overview`, `/api/reports/sales`, `/api/reports/inventory-movements` | hai report hỗ trợ `from`, `to`, `branchId` theo scope                                            |
| Subscription          | `/api/subscriptions/current`, `/api/subscriptions/store/{storeId}`                  | user thuộc store đọc subscription đã được tenant-scope; toàn bộ/cập nhật chỉ cho system admin    |
| Audit log             | `/api/audit-logs`, `/api/audit-logs/store/{storeId}`                                | pagination/filter ở server; log toàn hệ thống chỉ system admin, log theo store được tenant-scope |

Swagger/OpenAPI là nguồn endpoint/DTO chính xác nhất khi được bật trong môi trường phát triển: `GET /swagger-ui.html`. Nó tắt mặc định ở runtime; đặt `SPRINGDOC_API_DOCS_ENABLED=true` và `SPRINGDOC_SWAGGER_UI_ENABLED=true` trong môi trường phát triển đáng tin cậy.

## Báo cáo sales

```http
GET /api/reports/sales?from=2026-09-01&to=2026-09-07&branchId=12
```

`from`/`to` là ISO date, inclusive, tối đa 366 ngày. Nếu bỏ trống, API lấy 7 ngày tính đến hôm nay. `branchId` tùy chọn. `ROLE_ADMIN` xem toàn hệ thống khi không truyền branch; store admin/manager xem các branch trong store; branch manager bị khóa vào branch được gán. Cashier không có quyền report. Response bao gồm gross sales, refunds, net sales, average order value, daily series, payment breakdown và top products; số liệu được tính tại server.

## Báo cáo biến động tồn kho

```http
GET /api/reports/inventory-movements?from=2026-09-01&to=2026-09-07&branchId=12
```

Endpoint này có cùng quy tắc ngày, default 7 ngày, giới hạn 366 ngày và scope branch như báo cáo sales. Chỉ `ROLE_ADMIN`, `ROLE_STORE_ADMIN`, `ROLE_STORE_MANAGER` và `ROLE_BRANCH_MANAGER` được gọi. Khi không truyền `branchId`, system admin nhận aggregate toàn hệ thống, store admin/manager nhận aggregate tất cả branch của store, còn branch manager chỉ nhận branch được gán. Truyền branch ngoài scope trả `403`.

Số liệu được tổng hợp từ nhật ký bất biến `inventory_movement`: tổng số lượt, số lượng nhập/xuất (đều là số dương), thay đổi ròng có dấu, chuỗi theo ngày kể cả ngày không có biến động, breakdown theo loại movement và tối đa 10 sản phẩm có tổng khối lượng biến động lớn nhất. Đây là report theo audit trail, không phải snapshot tồn kho hiện tại.

## Lịch sử mua hàng của khách

```http
GET /api/customers/42/history?page=0&pageSize=20
```

`page` bắt đầu từ 0; `pageSize` nằm trong khoảng 1–100. Response chỉ trả DTO cần cho lịch sử: đơn hàng, branch, thời gian, payment/status, tổng tiền, tổng refund đã ghi nhận ở server và tiền ròng. System admin có thể tra cứu toàn hệ thống; store admin/manager chỉ tra cứu customer thuộc store của họ; branch manager/cashier chỉ nhận các order của branch được gán. Customer ngoài tenant trả `403` và không tạo query lịch sử.

## Lỗi

Mọi lỗi nghiệp vụ/validation trả JSON `ApiError` với `status`, `error`, `message`, `path` và (khi có) `fieldErrors`.

| Status | Ý nghĩa                                                          |
| ------ | ---------------------------------------------------------------- |
| 400    | payload/validation không hợp lệ                                  |
| 401    | không có hoặc JWT không hợp lệ/hết hạn                           |
| 403    | role hoặc tenant scope không hợp lệ                              |
| 404    | tài nguyên không tồn tại trong phạm vi cho phép                  |
| 409    | xung đột nghiệp vụ, như thiếu tồn kho hoặc idempotency key trùng |

Client phải hiển thị `message` cho người dùng và không tự retry POST order với key mới. Retry cùng `Idempotency-Key` sẽ trả lại order cũ nếu request gốc đã thành công.

## Subscription

Store mới và store cũ được migration backfill đều có subscription `FREE` trial 14 ngày. Backend trả các quota branch/employee/product trong response subscription và kiểm tra quota khi tạo các tài nguyên tương ứng. System admin có thể cập nhật `plan`, `status`, và tùy chọn `trialEnd`/`currentPeriodEnd`:

```http
PUT /api/subscriptions/store/12
Content-Type: application/json

{
  "plan": "BASIC",
  "status": "ACTIVE",
  "currentPeriodEnd": "2026-10-01T00:00:00"
}
```

Đây là quản trị dữ liệu nội bộ, không phải thanh toán. Thanh toán dùng `POST /api/subscriptions/stripe/checkout`, `POST /api/subscriptions/stripe/portal` và webhook `POST /api/subscriptions/stripe/webhook`; client không được diễn giải một lần cập nhật API hoặc redirect Checkout là thanh toán thành công. Backend chỉ đồng bộ sau webhook Stripe có chữ ký hợp lệ. Các endpoint Stripe trả lỗi cấu hình khi `STRIPE_*` chưa đầy đủ; xem [checklist external integration](external-integrations.md).

## Audit log

Backend ghi audit generic cho thay đổi trạng thái store, tạo/cập nhật/đổi role/xóa nhân sự, tạo order, tạo refund và cập nhật subscription. `inventory_movement` vẫn là audit chuyên biệt cho thay đổi tồn kho.

```http
GET /api/audit-logs?storeId=12&page=0&pageSize=50&action=ORDER_CREATED&entityType=Order&from=2026-09-01T00:00:00&to=2026-09-01T23:59:59
GET /api/audit-logs/store/12?page=0&pageSize=50&action=ORDER_CREATED
```

Hai endpoint đều trả `AuditLogPageDTO` gồm `page`, `pageSize`, `totalElements`, `totalPages` và mảng `logs`, được sắp theo `createdAt DESC, id DESC`. `page` bắt đầu từ 0; `pageSize` trong khoảng 1–100 (mặc định 50). `action` và `entityType` là so khớp chính xác không phân biệt hoa/thường sau khi trim. `from`/`to` là ISO local date-time, inclusive; nếu truyền cả hai thì `from` không được sau `to`.

`GET /api/audit-logs` chỉ dành cho `ROLE_ADMIN`; `storeId` là filter tùy chọn và được kiểm tra tồn tại. `GET /api/audit-logs/store/{storeId}` dành cho `ROLE_ADMIN`, `ROLE_STORE_ADMIN`, `ROLE_STORE_MANAGER`, nhưng service xác minh store ownership/assignment trước khi thực hiện query. Client không được dùng global endpoint để suy diễn log tenant khác.
