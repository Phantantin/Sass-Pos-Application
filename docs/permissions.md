# Phân quyền và phạm vi dữ liệu

## Role

| Role                  | Phạm vi chính                                                                             |
| --------------------- | ----------------------------------------------------------------------------------------- |
| `ROLE_ADMIN`          | vận hành toàn SaaS, xem/moderate store và dữ liệu không giới hạn tenant                   |
| `ROLE_STORE_ADMIN`    | chủ một hoặc nhiều store: branch, employee, catalog, inventory và giao dịch trong store được chọn |
| `ROLE_STORE_MANAGER`  | quản lý catalog, inventory, nhân sự store theo endpoint cho phép và giao dịch trong store |
| `ROLE_BRANCH_MANAGER` | dữ liệu/quản trị inventory của branch được gán; xem giao dịch/ca của branch               |
| `ROLE_BRANCH_CASHIER` | mở/đóng ca, POS, customer và giao dịch của branch được gán                                |

## Quyền theo nhóm nghiệp vụ

| Nhóm                              | Admin                                  | Store admin    | Store manager  | Branch manager            | Cashier                   |
| --------------------------------- | -------------------------------------- | -------------- | -------------- | ------------------------- | ------------------------- |
| Store moderation và toàn bộ store | Có                                     | Không          | Không          | Không                     | Không                     |
| Branch tạo/xóa                    | Có                                     | Có trong store | Không          | Không                     | Không                     |
| Nhân sự store                     | Có                                     | Có             | Có             | Không                     | Không                     |
| Nhân sự branch                    | Có                                     | Có             | Có             | Có trong branch           | Không                     |
| Category/Product ghi              | Có                                     | Có             | Có             | Không                     | Không                     |
| Inventory ghi                     | Có                                     | Có trong store | Có trong store | Có trong branch           | Không                     |
| Inventory xem                     | Có                                     | Có trong store | Có trong store | Có trong branch           | Có trong branch           |
| Customer tạo/sửa/xem              | Có                                     | Có             | Có             | Có                        | Có                        |
| Customer xóa                      | Có                                     | Có             | Có             | Có                        | Không                     |
| Customer purchase history         | Có toàn hệ thống                       | Có trong store | Có trong store | Chỉ order branch được gán | Chỉ order branch được gán |
| POS, order, refund, shift         | Có                                     | Có             | Có             | Có                        | Có                        |
| Báo cáo sales / biến động tồn kho | Có toàn hệ thống hoặc theo branch chọn | Có trong store | Có trong store | Có trong branch được gán  | Không                     |
| Lịch làm/chấm công                | Có toàn hệ thống                       | Có trong store | Có trong store | Có trong branch được gán  | Chỉ dữ liệu của mình      |
| Lập/xác nhận bảng lương           | Có                                      | Có trong store | Có trong store | Không                     | Không                     |
| Xem bảng lương                    | Có                                      | Có trong store | Có trong store | Có trong branch được gán  | Chỉ bảng lương của mình   |
| Điều chuyển tồn kho               | Có                                      | Có trong store | Có trong store | Yêu cầu/duyệt trong scope | Không                     |
| Subscription xem                  | Có mọi store                           | Có trong store | Có trong store | Có trong store            | Có trong store            |
| Subscription đổi plan/trạng thái  | Có                                     | Không          | Không          | Không                     | Không                     |
| Audit log toàn hệ thống           | Có                                     | Không          | Không          | Không                     | Không                     |
| Audit log theo store              | Có                                     | Có trong store | Có trong store | Không                     | Không                     |

Ma trận là tóm tắt quyền mong đợi từ annotation controller và service guard. Một request vẫn có thể bị từ chối nếu tài khoản chưa được gán store/branch, store không active hoặc bản ghi thuộc tenant khác.

## Hai lớp kiểm soát

1. Spring Security yêu cầu JWT cho `/api/**`; `@PreAuthorize` chặn role không phù hợp tại boundary.
2. Service kiểm tra quan hệ store/branch để hạn chế IDOR. Đặc biệt inventory, order, refund, shift, branch, catalog và customer áp dụng scope từ current user.

Frontend dùng `can()` trong `sass-pos-web/src/lib/auth/roles.ts` để ẩn menu không liên quan. Không được dựa vào UI để bảo vệ dữ liệu: mọi client, kể cả BFF, phải xử lý `401`/`403` từ backend.

## Quy tắc giao dịch

- Cashier và branch manager chỉ thao tác dữ liệu branch được gán.
- POS bỏ qua `branchId`, `cashier`, `totalAmount` và đơn giá do client gửi; server dùng branch/cashier hiện tại và product price trong database.
- Refund chỉ áp dụng cho order tại branch hiện tại, trong ca đang mở và tối đa số lượng đã bán trừ số lượng đã hoàn.
- Inventory adjustment thủ công yêu cầu lý do khi số lượng thay đổi; xóa chỉ cho phép khi quantity bằng 0 và phải có lý do.

## Subscription và audit log

- Theo yêu cầu vận hành hiện tại, `FREE`, `BASIC` và `PRO` đều không giới hạn số branch, employee hoặc product. Write boundary vẫn kiểm tra subscription còn hiệu lực; cấu trúc limit được giữ để có thể cấu hình chính sách khác sau này.
- Subscription ở trạng thái `TRIALING` hoặc `ACTIVE` mới được tạo thêm branch, employee, product. Khi trial/chu kỳ hết hạn, backend chuyển trạng thái thành `EXPIRED` khi kiểm tra entitlement và từ chối lần tạo mới đó.
- `GET /api/subscriptions/current` dành cho mọi role thuộc store. `GET /api/subscriptions/store/{storeId}` được service scope theo store; `GET /api/subscriptions` và `PUT /api/subscriptions/store/{storeId}` chỉ dành cho `ROLE_ADMIN`.
- `GET /api/audit-logs` chỉ dành cho `ROLE_ADMIN`; admin có thể truyền `storeId` để lọc một store. `GET /api/audit-logs/store/{storeId}` dành cho admin, store admin và store manager nhưng vẫn kiểm tra tenant scope tại service. Cả hai endpoint bắt buộc phân trang server-side (`page`, `pageSize` tối đa 100) và chỉ cho phép filter `action`, `entityType`, `from`, `to` trong scope đã được xác minh.
- `ROLE_STORE_ADMIN` có thể tạo Stripe Checkout/Customer Portal cho store của mình. Webhook Stripe không dùng JWT nhưng bắt buộc signing secret hợp lệ trước khi thay đổi trạng thái; không có luồng thanh toán giả lập. `ROLE_ADMIN` vẫn chỉ quản trị subscription nội bộ qua API.

## Báo cáo tồn kho

- `GET /api/reports/sales` và `GET /api/reports/inventory-movements` chỉ nhận `ROLE_ADMIN`, `ROLE_STORE_ADMIN`, `ROLE_STORE_MANAGER` hoặc `ROLE_BRANCH_MANAGER`; cashier không được cấp quyền report.
- Nếu không truyền `branchId`, admin xem aggregate toàn hệ thống; store admin/manager chỉ xem aggregate của store; branch manager chỉ xem branch đã gán. Khi truyền `branchId`, service vẫn xác minh branch đó nằm trong scope trước khi tổng hợp dữ liệu.
