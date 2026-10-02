package ru.todo.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskControllerTest {

    @Test
    void completingRepeatingTaskReadsItWithRowLock() {
        TaskRepository tasks = mock(TaskRepository.class);
        TaskController controller = new TaskController(tasks, new SimpleMeterRegistry());

        Task task = new Task("42", "20990101", "test", "", "d 1");
        when(tasks.findForUpdate(42L)).thenReturn(Optional.of(task));
        when(tasks.updateDate(42L, "20990102")).thenReturn(true);

        assertEquals(Map.of(), controller.done("42"));

        verify(tasks).findForUpdate(42L);
        verify(tasks, never()).find(42L);
        verify(tasks).updateDate(42L, "20990102");
    }
}