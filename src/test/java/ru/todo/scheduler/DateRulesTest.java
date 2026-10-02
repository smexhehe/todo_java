package ru.todo.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateRulesTest {
    private static final LocalDate NOW = LocalDate.of(2024, 1, 26);

    @Test
    void calculatesDailyYearlyWeeklyAndMonthlyDates() {
        assertEquals("20240127", DateRules.nextDate(NOW, "20240113", "d 7"));
        assertEquals("20250301", DateRules.nextDate(NOW, "20240229", "y"));
        assertEquals("20240129", DateRules.nextDate(NOW, "20240125", "w 1,2,3"));
        assertEquals("20240228", DateRules.nextDate(NOW, "20240222", "m -2"));
        assertEquals("20240810", DateRules.nextDate(NOW, "20240329", "m 10,17 12,8,1"));
    }

    @Test
    void validatesAndNormalizesTaskDates() {
        assertEquals("20240126", DateRules.normalizeDate("", "", NOW));
        assertEquals("20240126", DateRules.normalizeDate("20230126", "", NOW));
        assertEquals("20240130", DateRules.normalizeDate("20231225", "d 12", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> DateRules.normalizeDate("20240192", "", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> DateRules.nextDate(NOW, "20240126", "d 401"));
        assertThrows(IllegalArgumentException.class,
                () -> DateRules.nextDate(NOW, "20240126", "m -3"));
    }
}
