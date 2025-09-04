package org.dacrewj.contract;

import java.time.Instant;

public record DacrewWork(
		String id,
		Source source,
		Payload payload,
		Instant createdAt) {
}
