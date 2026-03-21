package com.ai.dev.garage.bot.adapter.in.web;

import com.ai.dev.garage.bot.adapter.in.web.dto.TodoSummaryView;
import com.ai.dev.garage.bot.application.port.in.TodoManagement;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.TodoSource;
import com.ai.dev.garage.bot.domain.TodoStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/ui/todos")
@RequiredArgsConstructor
public class TodoViewController {

    private static final int DEFAULT_LIMIT = 50;

    private final TodoManagement todoManagement;
    private final TodoViewDtoMapper mapper;
    private final WorkspacePathResolver workspacePathResolver;

    @GetMapping
    public String list(
            @RequestParam(required = false) TodoStatus status,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {
        List<TodoSummaryView> todos = mapper.toSummaryList(
            todoManagement.listTodos(status, DEFAULT_LIMIT, sort, dir));
        model.addAttribute("todos", todos);
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentSort", sort);
        model.addAttribute("currentDir", dir);
        model.addAttribute("statuses", TodoStatus.values());
        model.addAttribute("workspacePaths", workspacePathResolver.resolve());
        return "true".equals(hxRequest) ? "fragments/todo-table :: table" : "todos/list";
    }

    @PostMapping
    public String create(
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String workspace,
            Model model) {
        Requester webRequester = Requester.builder()
            .telegramUserId(0L)
            .telegramChatId(0L)
            .telegramUsername("web")
            .build();
        todoManagement.createTodo(title, description, TodoSource.WEB, webRequester, workspace);
        model.addAttribute("todos", mapper.toSummaryList(todoManagement.listTodos(null, DEFAULT_LIMIT)));
        model.addAttribute("statuses", TodoStatus.values());
        return "fragments/todo-table :: table";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        TodoSummaryView view = mapper.toSummary(todoManagement.getTodo(id));
        model.addAttribute("todo", view);
        model.addAttribute("workspacePaths", workspacePathResolver.resolve());
        return "todos/detail";
    }

    @PostMapping("/{id}/done")
    public String markDone(
            @PathVariable long id,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {
        todoManagement.markDone(id);
        if ("true".equals(hxRequest)) {
            return refreshTable(model);
        }
        return detail(id, model);
    }

    @PostMapping("/{id}/cancel")
    public String cancel(
            @PathVariable long id,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {
        todoManagement.cancel(id);
        if ("true".equals(hxRequest)) {
            return refreshTable(model);
        }
        return detail(id, model);
    }

    @PostMapping("/{id}/work")
    public String workOn(
            @PathVariable long id,
            @RequestParam String mode,
            @RequestParam(required = false) String workspace,
            Model model) {
        todoManagement.workOn(id, mode, workspace);
        return detail(id, model);
    }

    private String refreshTable(Model model) {
        model.addAttribute("todos", mapper.toSummaryList(todoManagement.listTodos(null, DEFAULT_LIMIT)));
        return "fragments/todo-table :: table";
    }
}
