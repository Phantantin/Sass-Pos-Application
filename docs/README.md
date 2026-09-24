# Tài liệu SaaS POS

Tài liệu này mô tả hành vi hiện có trong mã nguồn, không phải một hợp đồng cho tính năng chưa được triển khai.

- [Kiến trúc](architecture.md): thành phần, luồng request và ranh giới tenant.
- [Xác thực](auth.md): JWT, BFF cookie và xử lý phiên.
- [Phân quyền](permissions.md): role, quyền thao tác và phạm vi dữ liệu.
- [API](api-integration.md): endpoint, header và payload giao dịch chính.
- [Cơ sở dữ liệu](database.md): ERD, migration Flyway và các ràng buộc.
- [Kiểm thử](testing.md): lệnh kiểm thử và phạm vi hiện có.
- [Triển khai](deployment.md): Docker Compose, cấu hình môi trường và checklist production.

Swagger UI/OpenAPI tắt mặc định trong runtime. Chỉ bật trên môi trường phát triển đáng tin cậy với `SPRINGDOC_API_DOCS_ENABLED=true` và `SPRINGDOC_SWAGGER_UI_ENABLED=true`; khi đó Swagger UI ở `/swagger-ui.html`, OpenAPI JSON ở `/v3/api-docs`.
