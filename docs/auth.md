# Xác thực và phiên đăng nhập

## Backend JWT

Hai endpoint dưới đây là public:

| Method | Endpoint       | Mô tả                                           |
| ------ | -------------- | ----------------------------------------------- |
| `POST` | `/auth/signup` | tạo một tài khoản `ROLE_STORE_ADMIN` và trả JWT |
| `POST` | `/auth/login`  | xác thực email/mật khẩu và trả JWT              |

`signup` chỉ nhận `fullName`, `email`, `password`, `phone`. Role do server đặt thành `ROLE_STORE_ADMIN`; client không thể đăng ký `ROLE_ADMIN` qua payload này.

JWT có subject là email và claim `authorities`. Secret bắt buộc dài tối thiểu 32 byte UTF-8. Thời hạn token lấy từ `JWT_EXPIRATION_MS` (mặc định 8.400.000 ms). API protected dùng header:

```http
Authorization: Bearer <jwt>
Accept-Language: vi
```

`Accept-Language` nhận `vi` hoặc `en`; `vi` là mặc định. CORS lấy từ `CORS_ALLOWED_ORIGINS`, không dùng wildcard khi `allowCredentials=true`.

## BFF của frontend

Trình duyệt không nên gọi `/auth/*` của backend trực tiếp trong triển khai chuẩn. Dùng các route cùng origin của Next.js:

| Method | BFF endpoint             | Upstream            |
| ------ | ------------------------ | ------------------- |
| `POST` | `/api/auth/signup`       | `POST /auth/signup` |
| `POST` | `/api/auth/login`        | `POST /auth/login`  |
| `POST` | `/api/auth/logout`       | xóa cookie cục bộ   |
| `*`    | `/api/backend/[...path]` | `/api/...` backend  |

Khi login/signup thành công, BFF lấy JWT từ upstream, đặt cookie `sass_pos_session` với `HttpOnly`, `SameSite=Lax`, `Path=/` và chỉ bật `Secure` khi `NODE_ENV=production`. Response gửi về UI chỉ chứa user/message đã lọc, không chứa trường `jwt`. Proxy tự thêm Bearer header vào upstream request và xóa cookie nếu backend trả `401`.

`proxy.ts` của Next.js chuyển người chưa có cookie từ các trang protected về màn hình login. Đây chỉ là lớp điều hướng; endpoint backend vẫn phải xác thực JWT.

## Vận hành an toàn

- Tạo `JWT_SECRET` duy nhất cho từng environment và không commit `.env`.
- Bật HTTPS ở production để cookie `Secure` có hiệu lực.
- Chỉ khai báo origin frontend thực tế trong `CORS_ALLOWED_ORIGINS`.
- Đổi secret sẽ làm toàn bộ phiên hiện tại hết hiệu lực.
- Giữ `SPRING_API_URL` là địa chỉ nội bộ có thể truy cập từ Next.js server; không đặt nó thành `NEXT_PUBLIC_*`.

## Lỗi

Backend trả lỗi dạng `ApiError`:

```json
{
  "timestamp": "2026-08-31T00:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Dữ liệu gửi lên không hợp lệ",
  "path": "/api/products",
  "fieldErrors": { "name": "Tên sản phẩm là bắt buộc" }
}
```

`401` biểu thị thiếu/hết hạn/không hợp lệ token; `403` biểu thị token hợp lệ nhưng thiếu role hoặc không thuộc tenant/branch cần thiết.
