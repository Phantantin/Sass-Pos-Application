# SaaS POS Backend

REST API đa tenant cho SaaS POS, xây bằng Java 17 và Spring Boot. Mỗi dữ liệu nghiệp vụ được scope theo `Store` và, khi cần, theo `Branch`.

## Thành phần chính

- Spring Boot 4, Spring Security method security, JWT và JPA/MySQL.
- Flyway migrations cho schema cài mới.
- Store, branch, employee, global catalog có kiểm duyệt, product, inventory/điều chuyển nội bộ, customer (kèm purchase history phân trang), POS order, refund, shift và dashboard.
- Báo cáo doanh thu/giá vốn/lợi nhuận, hiệu suất/ca, biến động tồn kho và export; lịch làm, chấm công và bảng lương theo lương cố định hoặc giờ công.
- Subscription theo store (trial, plan, quota) và audit log cho các thao tác quản trị/giao dịch quan trọng.
- Inventory có unique `(branch, product)`, version/lock và audit `inventory_movement`.
- API error thống nhất; tiền dùng `BigDecimal`; backend tự tính giá/tổng tiền order và refund.

## Yêu cầu

- JDK 17
- MySQL 8.0+ (hoặc dùng Docker Compose ở thư mục gốc)
- Không commit file `.env` hay credential thật.

## Cấu hình

Sao chép `.env.example` theo cách phù hợp với shell/IDE và đặt biến môi trường sau:

| Biến                   | Bắt buộc | Mặc định                                  |
| ---------------------- | -------- | ----------------------------------------- |
| `SERVER_PORT`          | Không    | `5000`                                    |
| `DB_URL`               | Không    | `jdbc:mysql://localhost:3306/sass_pos_db` |
| `DB_USERNAME`          | Không    | `root`                                    |
| `DB_PASSWORD`          | Có       | —                                         |
| `JWT_SECRET`           | Có       | —; tối thiểu 32 byte UTF-8                |
| `JWT_EXPIRATION_MS`    | Không    | `8400000`                                 |
| `CORS_ALLOWED_ORIGINS` | Không    | `http://localhost:3000`                   |
| `DDL_AUTO`             | Không    | `validate`                                |
| `FLYWAY_ENABLED`       | Không    | `true`                                    |

`DDL_AUTO=validate` là lựa chọn production mặc định. Không đổi sang `update` để thay Flyway migration.

## Chạy local

```powershell
# PowerShell, tại Sass-Pos-Application
$env:DB_PASSWORD = "your-local-password"
$env:JWT_SECRET = "at-least-32-unique-random-utf8-bytes"
.\mvnw.cmd spring-boot:run
```

API mặc định ở `http://localhost:5000`. Health check ở `GET /actuator/health`. OpenAPI/Swagger tắt mặc định; chỉ bật trong môi trường phát triển đáng tin cậy với `SPRINGDOC_API_DOCS_ENABLED=true` và `SPRINGDOC_SWAGGER_UI_ENABLED=true`.

## Kiểm thử và build

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Test profile dùng H2 trong bộ nhớ và không chạy Flyway. Cài đặt MySQL mới chạy các migration trong `src/main/resources/db/migration`.

## API và bảo mật

- `POST /auth/signup` chỉ tạo `ROLE_STORE_ADMIN`; client không thể tự chọn role admin.
- `POST /auth/login` trả JWT cho BFF/server-side client.
- Tất cả `/api/**` yêu cầu Bearer JWT; controller dùng `@PreAuthorize` và service kiểm tra store/branch để chặn IDOR.
- Frontend chuẩn gọi qua Next.js BFF, không lưu JWT trong `localStorage` hay `sessionStorage`.
- Order yêu cầu `Idempotency-Key`, ca đang mở và tồn kho hợp lệ. Refund kiểm tra phần hàng còn có thể hoàn rồi hoàn tồn trong cùng transaction.
- Báo cáo doanh thu/lợi nhuận và báo cáo biến động tồn kho lấy số liệu tại server theo tenant/branch scope; báo cáo tồn kho tổng hợp từ audit trail `inventory_movement`, không tin số liệu do client gửi.
- Lịch sử mua hàng customer trả DTO phân trang; refund/net amount được tính từ bản ghi giao dịch đã lưu, và branch manager/cashier chỉ thấy order của branch được gán.
- Khi tạo store, hệ thống tạo subscription `FREE` dùng thử 14 ngày. Các plan hiện không giới hạn số branch/employee/product; trạng thái subscription vẫn phải còn hiệu lực mới được tạo dữ liệu. `ROLE_ADMIN` có thể quản trị plan/trạng thái qua API, còn người dùng thuộc store chỉ đọc subscription trong phạm vi store của mình.
- Audit log ghi các thay đổi trạng thái store, thay đổi nhân sự, tạo order/refund và cập nhật subscription. `ROLE_ADMIN` xem toàn bộ log; store admin/manager chỉ xem log thuộc store của họ.

Tài liệu chi tiết:

- [Kiến trúc](docs/architecture.md)
- [Xác thực](docs/auth.md)
- [Phân quyền](docs/permissions.md)
- [Tích hợp API](docs/api-integration.md)
- [Database và migration](docs/database.md)
- [Kiểm thử](docs/testing.md)
- [Triển khai](docs/deployment.md)
- [Stripe và UploadThing](docs/external-integrations.md)

## Giới hạn hiện tại

Stripe Checkout, Customer Portal và đồng bộ webhook có chữ ký đã có trong code nhưng mặc định tắt. Chưa thể xem đây là thanh toán đã xác minh khi chưa có Stripe sandbox/live credential, recurring Price IDs, Customer Portal configuration và webhook signing secret. UploadThing cũng cần token V7 đã rotate để xác minh upload thật. Testcontainers MySQL đã có bài kiểm tra lock/overselling và tự skip nếu Docker tắt; vẫn cần chạy xác nhận với Docker/CI và mở rộng test concurrency ở HTTP workflow. Payroll chưa xử lý thuế/bảo hiểm/overtime hoặc chuyển tiền thật. Không có luồng thanh toán Stripe giả lập.
