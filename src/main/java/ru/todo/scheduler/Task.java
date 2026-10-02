package ru.todo.scheduler;

public record Task(String id, String date, String title, String comment, String repeat) {
}
