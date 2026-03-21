package com.ai.dev.garage.bot.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.dev.garage.bot.adapter.in.rest.dto.CreateTodoRequest;
import com.ai.dev.garage.bot.adapter.in.rest.dto.TodoResponse;
import com.ai.dev.garage.bot.adapter.in.rest.dto.UpdateTodoRequest;
import com.ai.dev.garage.bot.application.port.in.TodoManagement;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.Todo;
import com.ai.dev.garage.bot.domain.TodoSource;
import com.ai.dev.garage.bot.domain.TodoStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TodoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
@ActiveProfiles("test")
class TodoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TodoManagement todoManagement;

    @MockBean
    private TodoResponseMapper todoResponseMapper;

    @MockBean
    private JobResponseMapper jobResponseMapper;

    @Test
    void shouldReturnTodosWhenListTodos() throws Exception {
        Todo todo = Todo.builder()
            .id(1L).title("Test").status(TodoStatus.OPEN).source(TodoSource.WEB).build();
        when(todoManagement.listTodos(isNull(), eq(50), eq("createdAt"), eq("desc")))
            .thenReturn(List.of(todo));
        when(todoResponseMapper.toResponse(todo))
            .thenReturn(TodoResponse.builder().id(1L).title("Test").status("OPEN").build());

        mockMvc.perform(get("/todos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.todos").isArray())
            .andExpect(jsonPath("$.todos[0].id").value(1));
    }

    @Test
    void shouldReturnCreatedWhenCreateTodo() throws Exception {
        CreateTodoRequest req = new CreateTodoRequest();
        req.setTitle("New todo");

        Todo created = Todo.builder()
            .id(5L).title("New todo").status(TodoStatus.OPEN).source(TodoSource.WEB).build();
        when(todoManagement.createTodo(eq("New todo"), isNull(), eq(TodoSource.WEB),
            any(Requester.class), isNull()))
            .thenReturn(created);
        when(todoResponseMapper.toResponse(created))
            .thenReturn(TodoResponse.builder().id(5L).title("New todo").status("OPEN").build());

        mockMvc.perform(post("/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void shouldReturn400WhenCreateTodoMissingTitle() throws Exception {
        mockMvc.perform(post("/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnTodoWhenGetById() throws Exception {
        Todo todo = Todo.builder()
            .id(3L).title("Detail").status(TodoStatus.OPEN).source(TodoSource.WEB).build();
        when(todoManagement.getTodo(3)).thenReturn(todo);
        when(todoResponseMapper.toResponse(todo))
            .thenReturn(TodoResponse.builder().id(3L).title("Detail").status("OPEN").build());

        mockMvc.perform(get("/todos/3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void shouldReturnUpdatedTodoWhenUpdateTodo() throws Exception {
        UpdateTodoRequest req = new UpdateTodoRequest();
        req.setTitle("Updated");

        Todo updated = Todo.builder()
            .id(4L).title("Updated").status(TodoStatus.OPEN).source(TodoSource.WEB).build();
        when(todoManagement.updateTodo(4, "Updated", null)).thenReturn(updated);
        when(todoResponseMapper.toResponse(updated))
            .thenReturn(TodoResponse.builder().id(4L).title("Updated").status("OPEN").build());

        mockMvc.perform(put("/todos/4")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated"));
    }

    @Test
    void shouldReturnDoneTodoWhenMarkDone() throws Exception {
        Todo done = Todo.builder()
            .id(6L).title("Done").status(TodoStatus.DONE).source(TodoSource.WEB).build();
        when(todoManagement.markDone(6)).thenReturn(done);
        when(todoResponseMapper.toResponse(done))
            .thenReturn(TodoResponse.builder().id(6L).title("Done").status("DONE").build());

        mockMvc.perform(post("/todos/6/done"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void shouldReturnCancelledTodoWhenCancel() throws Exception {
        Todo cancelled = Todo.builder()
            .id(7L).title("Cancel").status(TodoStatus.CANCELLED).source(TodoSource.WEB).build();
        when(todoManagement.cancel(7)).thenReturn(cancelled);
        when(todoResponseMapper.toResponse(cancelled))
            .thenReturn(TodoResponse.builder().id(7L).status("CANCELLED").build());

        mockMvc.perform(post("/todos/7/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
