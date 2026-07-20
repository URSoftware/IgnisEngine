package com.ignis.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IgnisLoggerTest {

    @AfterEach
    void cleanUp() {
        IgnisLogger.clearRecentLogs();
    }

    @Test
    void exposesRecentLogsWithLevelFilterAndTailLimit() {
        IgnisLogger.clearRecentLogs();
        IgnisLogger.info("inicio");
        IgnisLogger.warn("aviso");
        IgnisLogger.error("falha");

        List<IgnisLogger.LogEntry> tail = IgnisLogger.recentLogs(2, null);
        assertEquals(List.of("aviso", "falha"), tail.stream().map(IgnisLogger.LogEntry::message).toList());

        List<IgnisLogger.LogEntry> warnings = IgnisLogger.recentLogs(10, IgnisLogger.Level.WARN);
        assertEquals(1, warnings.size());
        assertEquals("aviso", warnings.get(0).message());
    }
}
