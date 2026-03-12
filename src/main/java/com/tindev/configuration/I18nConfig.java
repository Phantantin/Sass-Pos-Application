package com.tindev.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Arrays;
import java.util.Locale;

@Configuration
public class I18nConfig {

    @Bean
    public ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        // Chỉ định tiền tố của file (messages_vi -> messages)
        source.setBasenames("messages");
        source.setDefaultEncoding("UTF-8");
        // Nếu không tìm thấy key, trả về chính cái key đó thay vì báo lỗi
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }

    @Bean
    public LocaleResolver localeResolver() {
        // AcceptHeaderLocaleResolver sẽ tự động đọc header "Accept-Language" từ React gửi lên
        AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
        // Ngôn ngữ mặc định là Tiếng Việt nếu Client không gửi header
        localeResolver.setDefaultLocale(new Locale("vi"));
        localeResolver.setSupportedLocales(Arrays.asList(new Locale("vi"), new Locale("en")));
        return localeResolver;
    }
}
