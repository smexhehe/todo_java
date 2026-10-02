package ru.todo.scheduler;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TaskRepository {
    private static final RowMapper<Task> TASK_MAPPER = (rs, rowNum) -> new Task(
            Long.toString(rs.getLong("id")), rs.getString("date"), rs.getString("title"),
            rs.getString("comment"), rs.getString("repeat"));
    private static final String COLUMNS = "SELECT id, date, title, comment, repeat FROM scheduler";
    private static final DateTimeFormatter SEARCH_DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private final JdbcTemplate jdbc;

    public TaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long create(Task task) {
        return jdbc.queryForObject(
                "INSERT INTO scheduler (date, title, comment, repeat) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, task.date(), task.title(), task.comment(), task.repeat());
    }

    public List<Task> list(String search) {
        if (search == null || search.isEmpty()) {
            return jdbc.query(COLUMNS + " ORDER BY date, id LIMIT 50", TASK_MAPPER);
        }
        try {
            LocalDate date = LocalDate.parse(search, SEARCH_DATE);
            return jdbc.query(COLUMNS + " WHERE date = ? ORDER BY date, id LIMIT 50",
                    TASK_MAPPER, date.format(DateRules.DATE_FORMAT));
        } catch (DateTimeParseException ignored) {
            return jdbc.query(COLUMNS + " WHERE title ILIKE ? OR comment ILIKE ? ORDER BY date, id LIMIT 50",
                    TASK_MAPPER, "%" + search + "%", "%" + search + "%");
        }
    }

public Optional<Task> find(long id) {
    try {
        return Optional.ofNullable(jdbc.queryForObject(
                COLUMNS + " WHERE id = ?", TASK_MAPPER, id));
    } catch (EmptyResultDataAccessException ignored) {
        return Optional.empty();
    }
}

public Optional<Task> findForUpdate(long id) {
    try {
        return Optional.ofNullable(jdbc.queryForObject(
                COLUMNS + " WHERE id = ? FOR UPDATE", TASK_MAPPER, id));
    } catch (EmptyResultDataAccessException ignored) {
        return Optional.empty();
    }
}

    public boolean update(long id, Task task) {
        return jdbc.update("UPDATE scheduler SET date = ?, title = ?, comment = ?, repeat = ? WHERE id = ?",
                task.date(), task.title(), task.comment(), task.repeat(), id) > 0;
    }

    public boolean updateDate(long id, String date) {
        return jdbc.update("UPDATE scheduler SET date = ? WHERE id = ?", date, id) > 0;
    }

    public boolean delete(long id) {
        return jdbc.update("DELETE FROM scheduler WHERE id = ?", id) > 0;
    }
}
