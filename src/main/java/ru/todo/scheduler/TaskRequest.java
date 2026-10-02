package ru.todo.scheduler;

public record TaskRequest(String id, String date, String title, String comment, String repeat) {
    Task normalized(String normalizedDate) {
        return new Task(id, normalizedDate, title, comment == null ? "" : comment,
                repeat == null ? "" : repeat);
    }
}
