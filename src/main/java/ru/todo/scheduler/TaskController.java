package ru.todo.scheduler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskController {

    @GetMapping(value = "/api/nextdate", produces = MediaType.TEXT_PLAIN_VALUE)
    public String nextDate(@RequestParam(required = false) String now,
                           @RequestParam(required = false) String date,
                           @RequestParam(required = false) String repeat) {
        LocalDate reference = now == null || now.isEmpty() ? LocalDate.now() : DateRules.parseDate(now);
        return DateRules.nextDate(reference, date, repeat);
    }

private final TaskRepository tasks;
private final Counter createdTasks;

public TaskController(TaskRepository tasks, MeterRegistry registry) {
    this.tasks = tasks;
    this.createdTasks = registry.counter("todo.tasks.created");
}

@PostMapping("/api/task")
public ResponseEntity<Map<String, Long>> add(@RequestBody TaskRequest request) {
    Task task = checkedTask(request);
    long id = tasks.create(task);
    createdTasks.increment();
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
}

    @GetMapping("/api/tasks")
    public Map<String, List<Task>> list(@RequestParam(defaultValue = "") String search) {
        return Map.of("tasks", tasks.list(search));
    }

    @GetMapping("/api/task")
    public Task get(@RequestParam(required = false) String id) {
        return tasks.find(parseId(id)).orElseThrow(TaskController::notFound);
    }

    @PutMapping("/api/task")
    public Map<String, String> update(@RequestBody TaskRequest request) {
        long id = parseId(request.id());
        Task task = checkedTask(request);
        if (!tasks.update(id, task)) {
            throw notFound();
        }
        return Map.of();
    }

    @DeleteMapping("/api/task")
    public Map<String, String> delete(@RequestParam(required = false) String id) {
        if (!tasks.delete(parseId(id))) {
            throw notFound();
        }
        return Map.of();
    }

    @Transactional
    @PostMapping("/api/task/done")
    public Map<String, String> done(@RequestParam(required = false) String id) {
        long taskId = parseId(id);
        Task task = tasks.findForUpdate(taskId).orElseThrow(TaskController::notFound);
        boolean changed;
        if (task.repeat().isEmpty()) {
            changed = tasks.delete(taskId);
        } else {
            String date = DateRules.nextDate(LocalDate.now(), task.date(), task.repeat());
            changed = tasks.updateDate(taskId, date);
        }
        if (!changed) {
            throw notFound();
        }
        return Map.of();
    }

    private static Task checkedTask(TaskRequest request) {
        if (request.title() == null || request.title().isEmpty()) {
            throw new IllegalArgumentException("title is empty");
        }
        String date = DateRules.normalizeDate(request.date(), request.repeat(), LocalDate.now());
        return request.normalized(date);
    }

    private static long parseId(String value) {
        if (value == null || value.isBlank()) {
            throw notFound();
        }
        try {
            long id = Long.parseLong(value);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // An invalid identifier has the same API response as a missing task.
        }
        throw notFound();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Задача не найдена");
    }
}
