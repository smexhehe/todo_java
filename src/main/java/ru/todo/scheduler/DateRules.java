package ru.todo.scheduler;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class DateRules {
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);

    private DateRules() {
    }

    public static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("invalid start date", exception);
        }
    }

    public static String normalizeDate(String date, String repeat, LocalDate today) {
        String value = date == null || date.isEmpty() ? today.format(DATE_FORMAT) : date;
        LocalDate start = parseDate(value);
        if (repeat != null && !repeat.isEmpty()) {
            String next = nextDate(today, value, repeat);
            if (start.isBefore(today)) {
                return next;
            }
        } else if (start.isBefore(today)) {
            return today.format(DATE_FORMAT);
        }
        return value;
    }

    public static String nextDate(LocalDate now, String date, String repeat) {
        if (repeat == null || repeat.isBlank()) {
            throw new IllegalArgumentException("repeat rule is empty");
        }
        LocalDate start = parseDate(date);
        String[] parts = repeat.trim().split("\\s+");
        LocalDate next = switch (parts[0]) {
            case "d" -> nextDaily(now, start, parts);
            case "y" -> nextYearly(now, start);
            case "w" -> nextWeekly(now, parts);
            case "m" -> nextMonthly(now, start, parts);
            default -> throw new IllegalArgumentException("unknown repeat rule");
        };
        return next.format(DATE_FORMAT);
    }

    private static LocalDate nextDaily(LocalDate now, LocalDate start, String[] parts) {
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid daily rule");
        }
        int interval = parseNumber(parts[1], "invalid daily interval");
        if (interval < 1 || interval > 400) {
            throw new IllegalArgumentException("invalid daily interval");
        }
        LocalDate next = start.plusDays(interval);
        while (!next.isAfter(now)) {
            next = next.plusDays(interval);
        }
        return next;
    }

    private static LocalDate nextYearly(LocalDate now, LocalDate start) {
        // Go's AddDate moves February 29 to March 1 in a non-leap year.
        LocalDate next = start.withDayOfMonth(1).plusYears(1).plusDays(start.getDayOfMonth() - 1L);
        while (!next.isAfter(now)) {
            next = next.plusYears(1);
        }
        return next;
    }

    private static LocalDate nextWeekly(LocalDate now, String[] parts) {
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid weekly rule");
        }
        Set<Integer> days = parseList(parts[1], 1, 7, "invalid weekday");
        LocalDate next = now.plusDays(1);
        for (int i = 0; i < 7; i++, next = next.plusDays(1)) {
            DayOfWeek day = next.getDayOfWeek();
            if (days.contains(day.getValue())) {
                return next;
            }
        }
        throw new IllegalArgumentException("invalid weekly rule");
    }

    private static LocalDate nextMonthly(LocalDate now, LocalDate start, String[] parts) {
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("invalid monthly rule");
        }
        Set<Integer> days = parseList(parts[1], -2, 31, "invalid monthly day");
        if (days.stream().anyMatch(day -> day == 0 || day < -2)) {
            throw new IllegalArgumentException("invalid monthly day");
        }
        Set<Integer> months = parts.length == 3
                ? parseList(parts[2], 1, 12, "invalid month") : Set.of();
        LocalDate next = (start.isAfter(now) ? start : now).plusDays(1);
        // A 400-year Gregorian cycle contains every possible month/day combination.
        for (int i = 0; i < 146097; i++, next = next.plusDays(1)) {
            int lastDay = next.lengthOfMonth();
            int day = next.getDayOfMonth();
            if ((months.isEmpty() || months.contains(next.getMonthValue()))
                    && (days.contains(day) || days.contains(-1) && day == lastDay
                    || days.contains(-2) && day == lastDay - 1)) {
                return next;
            }
        }
        throw new IllegalArgumentException("monthly rule has no possible date");
    }

    private static Set<Integer> parseList(String value, int min, int max, String error) {
        Set<Integer> parsed = new HashSet<>();
        Arrays.stream(value.split(",", -1)).forEach(part -> {
            int number = parseNumber(part, error);
            if (number < min || number > max) {
                throw new IllegalArgumentException(error);
            }
            parsed.add(number);
        });
        return parsed;
    }

    private static int parseNumber(String value, String error) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(error, exception);
        }
    }
}
