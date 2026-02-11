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

@Controller
@RequestMapping("/employee")
public class EmployeeController {

    private final EmployeeRepository employeeRepository;
    private final O2DConfigRepository o2dConfigRepository;

    private final com.company.flowmanagement.service.EmployeeService employeeService;

    public EmployeeController(EmployeeRepository employeeRepository, O2DConfigRepository o2dConfigRepository,
            com.company.flowmanagement.service.EmployeeService employeeService) {
        this.employeeRepository = employeeRepository;
        this.o2dConfigRepository = o2dConfigRepository;
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
    public String orderEntry(Model model, Authentication authentication) {
        String username = authentication.getName();
        model.addAllAttributes(employeeService.getEmployeeContext(username));
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
        return "employee-fms-folder";
    }
}
