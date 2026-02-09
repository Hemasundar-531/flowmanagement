package com.company.flowmanagement.service;

import com.company.flowmanagement.model.Employee;
import com.company.flowmanagement.model.Project;
import com.company.flowmanagement.model.Task;
import com.company.flowmanagement.model.User;
import com.company.flowmanagement.repository.EmployeeRepository;
import com.company.flowmanagement.repository.ProjectRepository;
import com.company.flowmanagement.repository.TaskRepository;
import com.company.flowmanagement.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository,
            EmployeeRepository employeeRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    // Generate unique task ID
    public String generateTaskId() {
        long count = taskRepository.count();
        return String.format("TASK-%03d", count + 1);
    }

    // Get dashboard stats for a user
    public Map<String, Object> getDashboardStats(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null)
            return new HashMap<>();

        List<Task> allTasks = taskRepository.findByAssignedToIdOrderByCreatedAtDesc(user.getId());
        long totalTasks = allTasks.size();
        long onTimeCount = allTasks.stream().filter(t -> "On Time".equals(t.getStatus())).count();

        // Calculate OTC score (On Time Completion)
        double otcScore = totalTasks > 0 ? (double) onTimeCount / totalTasks * 100 : 0;
        String otcScoreStr = String.format("%.0f%%", otcScore);

        // Calculate ATS score (assuming some logic, for now placeholder)
        String atsScore = "85%"; // Placeholder

        // Chart data
        Map<String, Long> statusCounts = allTasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()));

        List<Map<String, Object>> chartData = Arrays.asList(
                Map.of("name", "On Time", "value", statusCounts.getOrDefault("On Time", 0L), "color", "#22c55e"),
                Map.of("name", "In Progress", "value", statusCounts.getOrDefault("In Progress", 0L), "color",
                        "#3b82f6"),
                Map.of("name", "Delayed", "value", statusCounts.getOrDefault("Delayed", 0L), "color", "#facc15"),
                Map.of("name", "Overdue", "value", statusCounts.getOrDefault("Overdue", 0L), "color", "#ef4444"));

        return Map.of(
                "total_tasks", totalTasks,
                "on_time_count", onTimeCount,
                "otc_score", otcScoreStr,
                "ats_score", atsScore,
                "chart_data", chartData);
    }

    // Get client-project map for a user
    public Map<String, List<Project>> getClientProjectMap(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null)
            return new HashMap<>();

        // For simplicity, return all projects (in real app, filter by user access)
        List<Project> allProjects = projectRepository.findAll();

        return allProjects.stream()
                .collect(Collectors.groupingBy(Project::getClientName));
    }

    // Get tasks for user
    public Map<String, List<Task>> getUserTasks(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null)
            return new HashMap<>();

        List<Task> myActive = taskRepository.findActiveTasksByAssignedToId(user.getId());
        List<Task> myCompleted = taskRepository.findCompletedTasksByAssignedToId(user.getId());
        List<Task> delegated = taskRepository.findDelegatedTasksByAssignedById(user.getId());

        return Map.of(
                "myTasks", myActive,
                "completedTasks", myCompleted,
                "delegatedTasks", delegated);
    }

    // Create task
    public Task createTask(Task task) {
        task.setTaskId(generateTaskId());
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        return taskRepository.save(task);
    }

    // Update task status
    public Task updateTaskStatus(String taskId, String status, String remarks, String completionDate) {
        Task task = taskRepository.findByTaskId(taskId);
        if (task != null) {
            task.setStatus(status);
            task.setRemarks(remarks);
            task.setCompletionDate(completionDate);
            task.setUpdatedAt(Instant.now());
            return taskRepository.save(task);
        }
        return null;
    }

    // Bulk create tasks
    public List<Task> createBulkTasks(List<Task> tasks) {
        tasks.forEach(task -> {
            task.setTaskId(generateTaskId());
            task.setCreatedAt(Instant.now());
            task.setUpdatedAt(Instant.now());
        });
        return taskRepository.saveAll(tasks);
    }
}
