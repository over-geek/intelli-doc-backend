package com.intellidoc.ingestion.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class IngestionMetricsService {

    private final MeterRegistry meterRegistry;

    public IngestionMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordMessageSucceeded() {
        counter("intellidoc.ingestion.messages.completed").increment();
    }

    public void recordMessageRetried() {
        counter("intellidoc.ingestion.messages.retried").increment();
    }

    public void recordMessageDeadLettered() {
        counter("intellidoc.ingestion.messages.dead_lettered").increment();
    }

    public void recordStageCompleted(String stage, Duration duration) {
        Timer.builder("intellidoc.ingestion.stage.duration")
                .tag("stage", stage)
                .register(meterRegistry)
                .record(duration);
        counter("intellidoc.ingestion.stage.completed", "stage", stage).increment();
    }

    public void recordStageFailed(String stage) {
        counter("intellidoc.ingestion.stage.failed", "stage", stage).increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }
}
