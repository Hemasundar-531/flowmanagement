package com.company.flowmanagement.controller;

import com.company.flowmanagement.model.Employee;
import com.company.flowmanagement.model.O2DConfig;
import com.company.flowmanagement.repository.EmployeeRepository;
import com.company.flowmanagement.repository.O2DConfigRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Optional;
import com.company.flowmanagement.model.OrderEntry;
import com.company.flowmanagement.model.PlanningEntry;
import com.company.flowmanagement.repository.OrderEntryRepository;
import com.company.flowmanagement.repository.PlanningEntryRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import com.company.flowmanagement.service.TaskService;
import com.company.flowmanagement.repository.UserRepository;
import com.company.flowmanagement.model.User;
import com.company.flowmanagement.model.Task;
import com.company.flowmanagement.model.ProcessStep;
import java.time.LocalDate;

@Controller
@RequestMapping("/employee")
public class EmployeeController {

    private final EmployeeRepository employeeRepository;
    private final O2DConfigRepository o2dConfigRepository;
    private final OrderEntryRepository orderEntryRepository;
    private final PlanningEntryRepository planningEntryRepository;
    private final TaskService taskService;
    private final UserRepository userRepository;

    private final com.company.flowmanagement.service.EmployeeService employeeService;

    public EmployeeController(EmployeeRepository employeeRepository, O2DConfigRepository o2dConfigRepository,
            OrderEntryRepository orderEntryRepository, PlanningEntryRepository planningEntryRepository,
            TaskService taskService, UserRepository userRepository,
            com.company.flowmanagement.service.EmployeeService employeeService) {
        this.employeeRepository = employeeRepository;
        this.o2dConfigRepository = o2dConfigRepository;
        this.orderEntryRepository = orderEntryRepository;
        this.planningEntryRepository = planningEntryRepository;
        this.taskService = taskService;
        this.userRepository = userRepository;
        this.employeeService = employeeService;
    }

    @GetMapping("/dashboard")
    public String employeeDashboard(Model model, Authentication authentication) {
        String username = authentication.getName();
        Optional<Employee> employeeOpt = employeeRepository.findByName(username);

        if (employeeOpt.isPresent()) {
            List<String> perms = employeeOpt.get().getPermissions();
            if (perms != null) {
                if (perms.contains("ORDER_ENTRY")) {
                    return "redirect:/employee/order-entry";
                } else if (perms.contains("TASK_MANAGER")) {
                    return "redirect:/employee/task-manager";
                } else if (!perms.isEmpty()) {
                    return "redirect:/employee/fms";
                }
            }
        }

        // If no permissions or user not found, redirect to a default or error
        return "redirect:/employee/task-manager"; // Fallback
    }

    @GetMapping("/order-entry")
    public String orderEntry(@RequestParam(name = "folderId", required = false) String folderId,
            @RequestParam(name = "entryId", required = false) String entryId,
            @RequestParam(name = "planOrderId", required = false) String planOrderId,
            @RequestParam(name = "planStart", required = false) String planStart,
            @RequestParam(name = "planning", required = false) Boolean planning,
            @RequestParam(name = "saved", required = false) Boolean saved,
            Model model, Authentication authentication) {

        String username = authentication.getName();
        model.addAllAttributes(employeeService.getEmployeeContext(username));

        O2DConfig config = null;
        if (folderId != null && !folderId.isBlank()) {
            config = o2dConfigRepository.findById(folderId).orElse(null);
        }
        if (config == null) {
            List<O2DConfig> all = o2dConfigRepository.findAll();
            if (!all.isEmpty()) {
                config = all.get(0);
            }
        }

        if (config != null) {
            model.addAttribute("orderDetails", config.getOrderDetails());
            model.addAttribute("selectedFolderId", config.getId());
            model.addAttribute("configOrderId", config.getOrderId());
            model.addAttribute("configCustomerName", config.getCustomerName());
            model.addAttribute("configCompanyName", config.getCompanyName());
            model.addAttribute("configRawMaterial", config.getRawMaterial());
            model.addAttribute("configQuantity", config.getQuantity());
            model.addAttribute("configCDD", config.getCDD());
            model.addAttribute("configMPD", config.getMPD());
            model.addAttribute("configStartDate", config.getStartDate());
            model.addAttribute("processDetails", config.getProcessDetails());

            List<String> responsibleOptions = new ArrayList<>();
            for (var step : config.getProcessDetails()) {
                if (step.getResponsiblePerson() != null && !step.getResponsiblePerson().isBlank()) {
                    String value = step.getResponsiblePerson().trim();
                    if (!responsibleOptions.contains(value)) {
                        responsibleOptions.add(value);
                    }
                }
            }
            if (responsibleOptions.isEmpty()) {
                responsibleOptions.add("Employee");
            }
            model.addAttribute("responsibleOptions", responsibleOptions);

            List<OrderEntry> entries = orderEntryRepository.findByFolderIdOrderByCreatedAtDesc(config.getId());
            model.addAttribute("orderEntries", entries);
            model.addAttribute("totalOrders", entries.size());
            model.addAttribute("completedOrders", 0);
            model.addAttribute("pendingOrders", entries.size());

            List<Map<String, Object>> entryRows = new ArrayList<>();
            int sr = 1;
            List<String> pendingOrderIds = new ArrayList<>();
            for (OrderEntry entry : entries) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sr", sr++);
                row.put("entryId", entry.getId());
                row.put("orderId", entry.getOrderId());
                List<String> values = new ArrayList<>();
                if (config.getOrderDetails() != null) {
                    for (String detail : config.getOrderDetails()) {
                        String normalizedDetail = normalizeKey(detail);
                        String value = "";
                        if (entry.getFields() != null) {
                            if (entry.getFields().containsKey(normalizedDetail)) {
                                value = entry.getFields().get(normalizedDetail);
                            } else {
                                for (Map.Entry<String, String> field : entry.getFields().entrySet()) {
                                    if (normalizeKey(field.getKey()).equals(normalizedDetail)) {
                                        value = field.getValue();
                                        break;
                                    }
                                }
                            }
                        }
                        values.add(value == null ? "" : value);
                    }
                }
                row.put("values", values);
                String planningStatus = findFieldValue(entry.getFields(), "Planning Status", "planning_status");
                if (planningStatus == null || planningStatus.isBlank() || "-".equals(planningStatus)) {
                    planningStatus = "Pending";
                }
                row.put("planningStatus", planningStatus);
                if ("Pending".equalsIgnoreCase(planningStatus) && entry.getOrderId() != null
                        && !entry.getOrderId().isBlank()) {
                    String orderId = entry.getOrderId().trim();
                    if (!pendingOrderIds.contains(orderId)) {
                        pendingOrderIds.add(orderId);
                    }
                }
                entryRows.add(row);
            }
            model.addAttribute("orderEntryRows", entryRows);
            model.addAttribute("pendingOrderIds", pendingOrderIds);
            if (entryId != null && !entryId.isBlank()) {
                OrderEntry selected = orderEntryRepository.findById(entryId).orElse(null);
                if (selected != null) {
                    model.addAttribute("selectedEntry", selected);
                    model.addAttribute("selectedEntryFields", selected.getFields());
                }
            } else if (planOrderId != null && !planOrderId.isBlank()) {
                OrderEntry selected = orderEntryRepository
                        .findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(config.getId(), planOrderId.trim());
                if (selected != null) {
                    model.addAttribute("selectedEntry", selected);
                    model.addAttribute("selectedEntryFields", selected.getFields());
                }
            } else if (!entries.isEmpty()) {
                OrderEntry latest = entries.get(0);
                model.addAttribute("selectedEntry", latest);
                model.addAttribute("selectedEntryFields", latest.getFields());
            }

            List<PlanningEntry> planningEntries = planningEntryRepository
                    .findByFolderIdOrderByCreatedAtAsc(config.getId());
            List<Map<String, Object>> planningBlocks = new ArrayList<>();
            for (PlanningEntry planningEntry : planningEntries) {
                Map<String, Object> block = new LinkedHashMap<>();
                String planningOrderId = planningEntry.getOrderId();
                String planningStart = planningEntry.getStartDate();
                block.put("orderId", planningOrderId);
                block.put("startDate", planningStart);

                OrderEntry entryForPlan = null;
                if (planningOrderId != null && !planningOrderId.isBlank()) {
                    entryForPlan = orderEntryRepository
                            .findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(config.getId(), planningOrderId.trim());
                }
                Map<String, String> entryFields = entryForPlan != null ? entryForPlan.getFields() : null;
                block.put("customerName", findFieldValue(entryFields, "Customer Name", "customer_name"));
                block.put("companyName", findFieldValue(entryFields, "Company Name", "company_name"));

                List<Map<String, String>> rows = new ArrayList<>();
                if (planningStart != null && !planningStart.isBlank()) {
                    try {
                        var startDate = java.time.LocalDate.parse(planningStart.trim());
                        int planningSr = 1;
                        for (var step : config.getProcessDetails()) {
                            var row = new LinkedHashMap<String, String>();
                            row.put("sr", String.valueOf(planningSr++));
                            row.put("stepProcess", step.getStepProcess());
                            row.put("responsiblePerson", step.getResponsiblePerson());
                            row.put("targetType", step.getTargetType());
                            row.put("days", step.getDays() == null ? "" : String.valueOf(step.getDays()));
                            if (step.getDays() != null) {
                                row.put("targetDate", startDate.plusDays(step.getDays()).toString());
                            } else {
                                row.put("targetDate", "-");
                            }
                            row.put("status", "On Track");
                            rows.add(row);
                        }
                    } catch (Exception ignored) {
                        rows = List.of();
                    }
                }
                block.put("rows", rows);
                planningBlocks.add(block);
            }
            model.addAttribute("planningBlocks", planningBlocks);
        }
        model.addAttribute("saved", saved != null && saved);
        return "employee-order-entry";
    }

    @GetMapping("/fms")
    public String fmsMain(Model model, Authentication authentication) {
        String username = authentication.getName();
        model.addAllAttributes(employeeService.getEmployeeContext(username));
        return "employee-fms";
    }

    @GetMapping("/fms/folder1")
    public String fmsFolder1(Model model, Authentication authentication) {
        String username = authentication.getName();
        model.addAllAttributes(employeeService.getEmployeeContext(username));
        return "employee-fms-folder1";
    }

    @GetMapping("/fms/folder2")
    public String fmsFolder2(Model model, Authentication authentication) {
        String username = authentication.getName();
        model.addAllAttributes(employeeService.getEmployeeContext(username));
        return "employee-fms-folder2";
    }

    @GetMapping("/fms/{folderId}")
    public String fmsDynamicFolder(@PathVariable("folderId") String folderId, Model model,
            Authentication authentication) {
        String username = authentication.getName();
        model.addAllAttributes(employeeService.getEmployeeContext(username));

        // Check if employee has permission for this folder
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) model.getAttribute("permissions");
        if (permissions == null || !permissions.contains("FMS:" + folderId)) {
            return "redirect:/employee/dashboard";
        }

        // Try to load folder-specific template, fallback to generic template
        Optional<O2DConfig> folderOpt = o2dConfigRepository.findById(folderId);
        if (folderOpt.isPresent()) {
            model.addAttribute("currentFolder", folderOpt.get());
        }

        // Try folder-specific template first (e.g., employee-fms-folder1.html)
        // If it doesn't exist, Spring will throw an error, so we'll use a generic
        // template

        // --- TASK MANAGER INTEGRATION ---
        model.addAttribute("allEmployees", employeeService.getAllEmployees());
        User user = userRepository.findByUsername(username);

        if (user != null) {
            // Get dashboard stats
            Map<String, Object> stats = taskService.getDashboardStats(username);
            model.addAttribute("dashboardStats", stats);

            // Get client-project map
            Map<String, List<com.company.flowmanagement.model.Project>> clientProjectMap = taskService
                    .getClientProjectMap(username);
            model.addAttribute("clientProjectMap", clientProjectMap);

            // Get user tasks (standard tasks)
            Map<String, List<Task>> tasks = taskService.getUserTasks(username);
            model.addAttribute("myTasks", tasks.get("myTasks"));
            model.addAttribute("completedTasks", tasks.get("completedTasks"));
            model.addAttribute("delegatedTasks", tasks.get("delegatedTasks"));

            // --- FMS ORDER PROCESS TASKS ---
            // Fetch all planning entries for this folder (Active Orders)
            List<PlanningEntry> planningEntries = planningEntryRepository.findByFolderIdOrderByCreatedAtAsc(folderId);

            // Fetch all order entries to get details (Customer, Company etc.)
            List<OrderEntry> allEntries = orderEntryRepository.findByFolderIdOrderByCreatedAtDesc(folderId);

            // Map OrderID -> Latest OrderEntry
            Map<String, OrderEntry> latestOrders = new LinkedHashMap<>();
            if (allEntries != null) {
                for (OrderEntry entry : allEntries) {
                    if (entry.getOrderId() != null && !latestOrders.containsKey(entry.getOrderId())) {
                        latestOrders.put(entry.getOrderId(), entry);
                    }
                }
            }

            List<Map<String, String>> fmsOverdueTasks = new ArrayList<>();
            List<Map<String, String>> fmsCompletedTasks = new ArrayList<>();
            Map<String, List<Map<String, String>>> fmsPendingTasksByStep = new LinkedHashMap<>();

            try {
                if (folderOpt.isPresent() && folderOpt.get().getProcessDetails() != null) {
                    O2DConfig config = folderOpt.get();
                    int taskSr = 1;

                    System.out.println(
                            "FMS Folder: Processing " + planningEntries.size() + " orders for user: " + username);
                    for (PlanningEntry plan : planningEntries) {
                        String orderId = plan.getOrderId();
                        String startDateStr = plan.getStartDate();

                        if (orderId == null || startDateStr == null) {
                            continue;
                        }

                        LocalDate startDate = null;
                        try {
                            startDate = LocalDate.parse(startDateStr);
                        } catch (Exception e) {
                            continue;
                        }

                        OrderEntry orderDetails = latestOrders.get(orderId);
                        String customerName = findFieldValue(orderDetails != null ? orderDetails.getFields() : null,
                                "Customer Name", "customer_name");
                        String companyName = findFieldValue(orderDetails != null ? orderDetails.getFields() : null,
                                "Company Name", "company_name");

                        for (ProcessStep step : config.getProcessDetails()) {
                            // Check if step is assigned to current user
                            if (step.getResponsiblePerson() != null
                                    && step.getResponsiblePerson().trim().equalsIgnoreCase(username.trim())) {

                                Map<String, String> taskMap = new LinkedHashMap<>();
                                taskMap.put("sr", String.valueOf(taskSr++));
                                taskMap.put("orderId", orderId);
                                taskMap.put("customerName", customerName);
                                taskMap.put("companyName", companyName);
                                taskMap.put("responsiblePerson", step.getResponsiblePerson());
                                String stepName = step.getStepProcess();
                                taskMap.put("taskName", stepName);

                                // Calculate Target Date
                                if (step.getDays() != null) {
                                    taskMap.put("targetDate", startDate.plusDays(step.getDays()).toString());
                                } else {
                                    taskMap.put("targetDate", "-");
                                }

                                // Check status
                                String status = step.getStatus();
                                if (status == null || status.isBlank()) {
                                    status = "Pending";
                                }
                                taskMap.put("status", status);
                                taskMap.put("pdf", "");

                                // Categorize
                                boolean isOverdue = false;
                                if (taskMap.get("targetDate") != null && !taskMap.get("targetDate").equals("-")) {
                                    try {
                                        LocalDate target = LocalDate.parse(taskMap.get("targetDate"));
                                        if (target.isBefore(LocalDate.now()) && !"Completed".equalsIgnoreCase(status)) {
                                            isOverdue = true;
                                        }
                                    } catch (Exception e) {
                                    }
                                }

                                if ("Completed".equalsIgnoreCase(status)) {
                                    fmsCompletedTasks.add(taskMap);
                                } else if (isOverdue) {
                                    fmsOverdueTasks.add(taskMap);
                                } else {
                                    // Add to Grouped Map
                                    fmsPendingTasksByStep.computeIfAbsent(stepName, k -> new ArrayList<>())
                                            .add(taskMap);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("CRITICAL ERROR in FMS Folder Task Logic: " + e.getMessage());
                e.printStackTrace();
            }

            model.addAttribute("fmsOverdueTasks", fmsOverdueTasks);
            model.addAttribute("fmsCompletedTasks", fmsCompletedTasks);
            model.addAttribute("fmsPendingTasksByStep", fmsPendingTasksByStep);

            // --- TEMP DEBUG: INJECT MOCK DATA IF EMPTY ---
            if (fmsOverdueTasks.isEmpty()) {
                Map<String, String> mockOverdue = new LinkedHashMap<>();
                mockOverdue.put("sr", "1");
                mockOverdue.put("orderId", "MOCK-OVER");
                mockOverdue.put("customerName", "Test Client");
                mockOverdue.put("taskName", "Step 1 Mock (Debug)");
                mockOverdue.put("targetDate", "2025-01-01");
                mockOverdue.put("status", "Pending");
                fmsOverdueTasks.add(mockOverdue);
            }
            if (fmsPendingTasksByStep.isEmpty()) {
                // Mock Step 1
                List<Map<String, String>> step1List = new ArrayList<>();
                Map<String, String> m1 = new LinkedHashMap<>();
                m1.put("sr", "1");
                m1.put("orderId", "ORD-100");
                m1.put("customerName", "Alpha Corp");
                m1.put("taskName", "Step 1: Design");
                m1.put("targetDate", "2030-01-01");
                m1.put("status", "Pending");
                step1List.add(m1);
                fmsPendingTasksByStep.put("Step 1: Design", step1List);

                // Mock Step 2
                List<Map<String, String>> step2List = new ArrayList<>();
                Map<String, String> m2 = new LinkedHashMap<>();
                m2.put("sr", "1");
                m2.put("orderId", "ORD-101");
                m2.put("customerName", "Beta Inc");
                m2.put("taskName", "Step 7: QC");
                m2.put("targetDate", "2030-01-05");
                m2.put("status", "Pending");
                step2List.add(m2);
                fmsPendingTasksByStep.put("Step 7: QC", step2List);
            }
            // ---------------------------------------------
        }

        return "employee-fms-folder";
    }

    @GetMapping("/order-entry/entry")
    @ResponseBody
    public OrderEntry fetchEntry(@RequestParam("folderId") String folderId,
            @RequestParam("orderId") String orderId) {
        if (folderId == null || folderId.isBlank() || orderId == null || orderId.isBlank()) {
            return null;
        }
        return orderEntryRepository.findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(folderId.trim(), orderId.trim());
    }

    @PostMapping("/order-entry/entry")
    public String createOrderEntry(@RequestParam("folderId") String folderId,
            @RequestParam("orderId") String orderId,
            @RequestParam Map<String, String> params) {
        if (folderId == null || folderId.isBlank()) {
            return "redirect:/employee/order-entry";
        }
        Map<String, String> fields = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith("field_")) {
                fields.put(key.substring("field_".length()), value);
            }
        });

        OrderEntry entry = new OrderEntry();
        entry.setFolderId(folderId.trim());
        entry.setOrderId(orderId == null ? "" : orderId.trim());
        entry.setFields(fields);
        entry.setCreatedAt(Instant.now());
        orderEntryRepository.save(entry);

        return "redirect:/employee/order-entry?folderId=" + folderId + "&entryId=" + entry.getId() + "&saved=true";
    }

    @PostMapping("/order-entry/planning")
    public String submitPlanning(@RequestParam("folderId") String folderId,
            @RequestParam("orderId") String orderId,
            @RequestParam(name = "startDate", required = false) String startDate) {

        if (folderId == null || folderId.isBlank()) {
            return "redirect:/employee/order-entry";
        }

        String safeFolder = folderId.trim();
        String safeOrder = orderId == null ? "" : orderId.trim();
        String safeStart = startDate == null ? "" : startDate.trim();

        // 1. Save Planning Entry
        if (!safeStart.isBlank()) {
            PlanningEntry planningEntry = new PlanningEntry();
            planningEntry.setFolderId(safeFolder);
            planningEntry.setOrderId(safeOrder);
            planningEntry.setStartDate(safeStart);
            planningEntry.setCreatedAt(Instant.now());
            planningEntryRepository.save(planningEntry);
        }

        // 2. UPDATE ORDER STATUS -> PLANNED
        if (!safeOrder.isBlank()) {
            OrderEntry entry = orderEntryRepository
                    .findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(safeFolder, safeOrder);

            if (entry != null) {
                Map<String, String> fields = entry.getFields();
                if (fields == null) {
                    fields = new LinkedHashMap<>();
                }

                fields.put("planning_status", "Planned");
                entry.setFields(fields);

                orderEntryRepository.save(entry);
            }
        }

        return "redirect:/employee/order-entry?folderId=" + safeFolder;
    }

    @PostMapping("/order-entry/planning-status")
    public String updatePlanningStatus(@RequestParam("entryId") String entryId,
            @RequestParam("planningStatus") String planningStatus,
            @RequestParam("folderId") String folderId) {
        if (entryId == null || entryId.isBlank()) {
            return "redirect:/employee/order-entry?folderId=" + (folderId == null ? "" : folderId.trim());
        }
        OrderEntry entry = orderEntryRepository.findById(entryId.trim()).orElse(null);
        if (entry != null) {
            Map<String, String> fields = entry.getFields();
            if (fields == null) {
                fields = new LinkedHashMap<>();
            }
            String statusValue = planningStatus == null ? "" : planningStatus.trim();
            fields.put("planning_status", statusValue);
            entry.setFields(fields);
            orderEntryRepository.save(entry);
        }
        String safeFolder = folderId == null ? "" : folderId.trim();
        return "redirect:/employee/order-entry?folderId=" + safeFolder;
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase();
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return normalized;
    }

    private static String findFieldValue(Map<String, String> fields, String label, String defaultKey) {
        if (fields == null || fields.isEmpty()) {
            return "-";
        }
        if (defaultKey != null && fields.containsKey(defaultKey)) {
            String val = fields.get(defaultKey);
            return (val == null || val.isBlank()) ? "-" : val;
        }
        String normalizedLabel = normalizeKey(label);
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (normalizeKey(entry.getKey()).equals(normalizedLabel)) {
                String val = entry.getValue();
                return (val == null || val.isBlank()) ? "-" : val;
            }
        }
        return "-";
    }
}
