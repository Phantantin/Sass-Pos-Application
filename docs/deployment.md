# Triển khai Docker

Tại thư mục gốc workspace có `docker-compose.yml`, xây MySQL 8.4, backend Java 17 và frontend Next.js standalone.

## Chuẩn bị

```powershell
Copy-Item .env.example .env
# sửa MYSQL_PASSWORD, MYSQL_ROOT_PASSWORD và JWT_SECRET trong .env
docker compose up --build
```

Không commit `.env`. `JWT_SECRET` phải dài tối thiểu 32 byte và khác ở mỗi môi trường. Đặt `CORS_ALLOWED_ORIGINS` thành origin frontend thật khi không dùng compose local.

## Health checks

- MySQL: `mysqladmin ping`.
- Backend: `GET /actuator/health`.
- Frontend: `GET /manifest.webmanifest`.

Frontend chỉ start sau backend healthy; backend chỉ start sau MySQL healthy. Production nên đặt reverse proxy/TLS ở phía trước, dùng managed secret store và backup MySQL định kỳ.

## Production checklist

- Chạy `mvn test`, lint/type-check/Vitest/frontend build trước image build.
- Chạy Flyway trên staging với snapshot database trước khi production.
- Bật HTTPS để cookie session có `Secure`.
- Giữ Swagger/OpenAPI tắt mặc định; chỉ đặt cả `SPRINGDOC_API_DOCS_ENABLED=true` và `SPRINGDOC_SWAGGER_UI_ENABLED=true` trên môi trường phát triển đáng tin cậy.
- Theo dõi logs/health, đặt resource limits và xoay JWT secret theo quy trình có chủ đích (đổi secret làm hết phiên hiện có).

Stripe/subscription payment không được bật trong Compose mặc định. Khi đã hoàn tất sandbox verification, inject đầy đủ `STRIPE_*` từ secret store cho từng môi trường; không bake credential vào image hay commit `.env`. Xem [checklist external integration](external-integrations.md).
