package ru.todo.scheduler;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
final class ProcessMemoryMetrics implements MeterBinder {
    private static final Path STATUS = Path.of("/proc/self/status");

    @Override
    public void bindTo(MeterRegistry registry) {
        if (!Files.isReadable(STATUS)) {
            return;
        }
        Gauge.builder("todo.process.rss", ProcessMemoryMetrics::residentBytes)
                .description("Resident memory used by the Java process")
                .baseUnit("bytes")
                .register(registry);
    }

    private static double residentBytes() {
        try (BufferedReader reader = Files.newBufferedReader(STATUS)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("VmRSS:")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 3 && "kB".equals(parts[2])) {
                        return Long.parseLong(parts[1]) * 1024d;
                    }
                    return Double.NaN;
                }
            }
        } catch (IOException | NumberFormatException exception) {
            return Double.NaN;
        }
        return Double.NaN;
    }
}
