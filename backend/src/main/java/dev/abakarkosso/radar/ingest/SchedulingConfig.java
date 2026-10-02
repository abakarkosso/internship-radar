package dev.abakarkosso.radar.ingest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turned off in tests so they never hit real job boards. */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty(name = "radar.ingest.enabled", matchIfMissing = true)
class SchedulingConfig {
}
