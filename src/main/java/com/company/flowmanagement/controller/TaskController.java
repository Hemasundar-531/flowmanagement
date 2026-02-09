package com.company.flowmanagement.controller;

import com.company.flowmanagement.model.Task;
import com.company.flowmanagement.model.User;
import com.company.flowmanagement.repository.UserRepository;
import com.company.flowmanagement.service.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/employee/task-manager")
public class TaskController {

    private final TaskService taskService;
    private final UserRepository userRepository;

    public TaskController(TaskService taskService, UserRepository userRepository) {
        this.taskService = taskService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String taskManager(Model model, Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username);

        if (user != null) {
            // Get dashboard stats
            Map<String, Object> stats = taskService.getDashboardStats(username);
            model.addAttribute("dashboardStats", stats);

            // Get client-project map
            Map<String, List<com.company.flowmanagement.model.Project>> clientProjectMap = taskService
                    .getClientProjectMap(username);
            model.addAttribute("clientProjectMap", clientProjectMap);

            // Get user tasks
            Map<String, List<Task>> tasks = taskService.getUserTasks(username);
            model.addAttribute("myTasks", tasks.get("myTasks"));
            model.addAttribute("completedTasks", tasks.get("completedTasks"));
            model.addAttribute("delegatedTasks", tasks.get("delegatedTasks"));
        }

        model.addAttribute("employeeName", username);
        return "employee-task-manager";
    }

    // API endpoints for AJAX operations

    @GetMapping("/api/dashboard-stats")
    @ResponseBody
    public ResponseEntity<?> getDashboardStats(Authentication authentication) {
        String username = authentication.getName();
        Map<String, Object> stats = taskService.getDashboardStats(username);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/api/tasks")
    @ResponseBody
    public ResponseEntity<?> getTasks(Authentication authentication) {
        String username = authentication.getName();
        Map<String, List<Task>> tasks = taskService.getUserTasks(username);
        return ResponseEntity.ok(tasks);
    }

    @PostMapping("/api/tasks")
    @ResponseBody
    public ResponseEntity<?> createTask(@ModelAttribute Task task,
            @RequestParam(required = false) MultipartFile file,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username);

            if (user != null) {
                task.setAssignedById(user.getId());
                task.setAssignedByName(username);

                // Handle file upload
                if (file != null && !file.isEmpty()) {
                    String fileName = saveFile(file);
                    task.setAssignedFile(fileName);
                }

                Task savedTask = taskService.createTask(task);
                return ResponseEntity.status(HttpStatus.CREATED).body(savedTask);
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/tasks/bulk")
    @ResponseBody
    public ResponseEntity<?> createBulkTasks(@RequestBody List<Task> tasks,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username);

            if (user != null) {
                tasks.forEach(task -> {
                    task.setAssignedById(user.getId());
                    task.setAssignedByName(username);
                });

                List<Task> savedTasks = taskService.createBulkTasks(tasks);
                return ResponseEntity.status(HttpStatus.CREATED).body(savedTasks);
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/api/tasks/{taskId}/complete")
    @ResponseBody
    public ResponseEntity<?> completeTask(@PathVariable String taskId,
            @RequestBody Map<String, String> payload,
            @RequestParam(required = false) MultipartFile file,
            Authentication authentication) {
        try {
            String remarks = payload.get("remarks");
            String completionDate = LocalDate.now().toString();

            // Handle file upload
            String fileName = null;
            if (file != null && !file.isEmpty()) {
                fileName = saveFile(file);
            }

            Task updatedTask = taskService.updateTaskStatus(taskId, "Completed", remarks, completionDate);
            if (updatedTask != null) {
                if (fileName != null) {
                    updatedTask.setCompletionFile(fileName);
                    taskService.createTask(updatedTask); // Save again with file
                }
                return ResponseEntity.ok(updatedTask);
            }

            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/api/tasks/bulk-complete")
    @ResponseBody
    public ResponseEntity<?> bulkCompleteTasks(@RequestBody List<String> taskIds,
            Authentication authentication) {
        try {
            String completionDate = LocalDate.now().toString();

            List<Task> updatedTasks = taskIds.stream()
                    .map(taskId -> taskService.updateTaskStatus(taskId, "Completed",
                            "Completed via bulk action", completionDate))
                    .filter(task -> task != null)
                    .toList();

            return ResponseEntity.ok(Map.of("updated", updatedTasks.size()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/projects")
    @ResponseBody
    public ResponseEntity<?> getProjects(Authentication authentication) {
        String username = authentication.getName();
        Map<String, List<com.company.flowmanagement.model.Project>> map = taskService.getClientProjectMap(username);
        List<com.company.flowmanagement.model.Project> projects = map.values().stream().flatMap(List::stream)
                .collect(Collectors.toList());
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/api/me")
    @ResponseBody
    public ResponseEntity<?> getMe(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(Map.of("username", username));
    }

    private String saveFile(MultipartFile file) throws IOException {
        String uploadDir = "uploads/tasks/";
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath);

        return fileName;
    }
}
