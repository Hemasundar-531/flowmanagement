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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employee")
public class EmployeeController {

    private final EmployeeRepository employeeRepository;
    private final O2DConfigRepository o2dConfigRepository;

    public EmployeeController(EmployeeRepository employeeRepository, O2DConfigRepository o2dConfigRepository) {
        this.employeeRepository = employeeRepository;
        this.o2dConfigRepository = o2dConfigRepository;
    }

    @GetMapping("/dashboard")
    public String employeeDashboard(Model model, Authentication authentication) {
        loadEmployeeContext(model, authentication);
        return "employee-dashboard";
    }

    @GetMapping("/order-entry")
    public String orderEntry(Model model, Authentication authentication) {
        loadEmployeeContext(model, authentication);
        return "employee-order-entry";
    }

    @GetMapping("/fms")
    public String fmsMain(Model model, Authentication authentication) {
        loadEmployeeContext(model, authentication);
        return "employee-fms";
    }

    @GetMapping("/fms/folder1")
    public String fmsFolder1(Model model, Authentication authentication) {
        loadEmployeeContext(model, authentication);
        return "employee-fms-folder1";
    }

    @GetMapping("/fms/folder2")
    public String fmsFolder2(Model model, Authentication authentication) {
        loadEmployeeContext(model, authentication);
        return "employee-fms-folder2";
    }

    @GetMapping("/fms/{folderId}")
    public String fmsDynamicFolder(@PathVariable("folderId") String folderId, Model model,
            Authentication authentication) {
        loadEmployeeContext(model, authentication);

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

    private void loadEmployeeContext(Model model, Authentication authentication) {
        String username = authentication.getName();

        Optional<Employee> employeeOpt = employeeRepository.findByName(username);
        Employee employee = employeeOpt.orElseGet(() -> {
            Employee e = new Employee();
            e.setName(username);
            e.setPermissions(new ArrayList<>());
            return e;
        });

        List<String> permissions = employee.getPermissions();
        if (permissions == null) {
            permissions = new ArrayList<>();
        }
        final List<String> finalPermissions = new ArrayList<>(permissions);

        // Fetch all FMS folders from database
        List<O2DConfig> allFmsFolders = o2dConfigRepository.findAll();

        // Filter folders based on employee permissions
        List<O2DConfig> employeeFmsFolders = allFmsFolders.stream()
                .filter(folder -> finalPermissions.contains("FMS:" + folder.getId()))
                .collect(Collectors.toList());

        System.out.println("DEBUG: Loading context for user: " + username);
        System.out.println("DEBUG: Permissions from Employee DB: " + finalPermissions);
        System.out.println("DEBUG: Employee has access to " + employeeFmsFolders.size() + " FMS folders");

        model.addAttribute("employeeName", employee.getName());
        model.addAttribute("permissions", finalPermissions);
        model.addAttribute("employee", employee);
        model.addAttribute("fmsFolders", employeeFmsFolders);
    }
}
