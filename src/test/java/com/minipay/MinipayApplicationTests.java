package com.minipay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.minipay.support.PostgresTestConfiguration;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(PostgresTestConfiguration.class)
class MinipayApplicationTests {

	@Test
	void contextLoads() {
	}

}
