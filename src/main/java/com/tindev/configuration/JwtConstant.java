package com.tindev.configuration;

public class JwtConstant {
    // Chìa khóa bí mật để ký và xác thực Token (Secret Key)
    // Lưu ý: Trong thực tế nên để chuỗi dài và phức tạp hơn để tránh bị hack
    public static final String JWT_SECRET = "fhsfhsdshsfgstweysfhosywhgyaspagfsfsfystdssdsgdwasgdssdhagdsfdsa";

    // Tên của Header mà Client (React) phải gửi kèm Token lên
    public static final String JWT_HEADER = "Authorization";
}