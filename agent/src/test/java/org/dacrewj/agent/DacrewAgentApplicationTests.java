package org.dacrewj.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = DacrewAgentApplication.class)
@ActiveProfiles("server")
class DacrewAgentApplicationTests {

	@Test
	void contextLoads() {
	}

}
