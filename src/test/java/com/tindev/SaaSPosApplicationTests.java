package com.tindev;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
class SaaSPosApplicationTests {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> UUID.randomUUID() + "-" + UUID.randomUUID());
	}

	@Test
	void contextLoads() {
	}

}
