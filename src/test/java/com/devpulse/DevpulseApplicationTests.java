package com.devpulse;

import com.devpulse.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DevpulseApplicationTests {

	@Test
	void contextLoads() {
	}

}
