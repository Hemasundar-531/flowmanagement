package com.company.flowmanagement.service;

import com.company.flowmanagement.model.Employee;
import com.company.flowmanagement.model.User;
import com.company.flowmanagement.repository.EmployeeRepository;
import com.company.flowmanagement.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Service for employee operations. When adding an employee, also creates
 * login credentials: username = employee name, default password = 1234567
 * (BCrypt), role = EMPLOYEE.
 */
@Service
public class EmployeeService {

    public static final String DEFAULT_EMPLOYEE_PASSWORD = "1234567";
    public static final String DEFAULT_EMPLOYEE_ROLE = "EMPLOYEE";

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.company.flowmanagement.repository.O2DConfigRepository o2dConfigRepository;

    public EmployeeService(EmployeeRepository employeeRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            com.company.flowmanagement.repository.O2DConfigRepository o2dConfigRepository) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.o2dConfigRepository = o2dConfigRepository;
    }

    /**
     * Save employee and create login credentials.
     * Username = employee name; password = 1234567 (BCrypt); role = EMPLOYEE.
     *
     * @throws IllegalArgumentException if name is blank or a user with this
     *                                  username already exists
     */
    public Employee createEmployee(Employee employee) {
        String name = employee.getName() != null ? employee.getName().trim() : "";
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Employee name is required.");
        }

        if (userRepository.findByUsername(name) != null) {
            throw new IllegalArgumentException(
                    "A login account with the name \"" + name + "\" already exists. Use a unique employee name.");
        }

        if (employee.getPermissions() == null) {
            employee.setPermissions(new ArrayList<>());
        }

        Employee saved = employeeRepository.save(employee);

        User user = new User();
        user.setUsername(name);
        user.setPassword(passwordEncoder.encode(DEFAULT_EMPLOYEE_PASSWORD));
        user.setRole(DEFAULT_EMPLOYEE_ROLE);
        user.setPermissions(new ArrayList<>(employee.getPermissions())); // Copy permissions to User
        userRepository.save(user);

        return saved;
    }

    public java.util.Map<String, Object> getEmployeeContext(String username) {
        java.util.Map<String, Object> context = new java.util.HashMap<>();

        com.company.flowmanagement.model.Employee employee = employeeRepository.findByName(username)
                .orElseGet(() -> {
                    com.company.flowmanagement.model.Employee e = new com.company.flowmanagement.model.Employee();
                    e.setName(username);
                    e.setPermissions(new ArrayList<>());
                    return e;
                });

        java.util.List<String> permissions = employee.getPermissions();
        if (permissions == null) {
            permissions = new ArrayList<>();
        }
        final java.util.List<String> finalPermissions = new ArrayList<>(permissions);

        // Fetch all FMS folders from database
        java.util.List<com.company.flowmanagement.model.O2DConfig> allFmsFolders = o2dConfigRepository.findAll();

        // Filter folders based on employee permissions
        java.util.List<com.company.flowmanagement.model.O2DConfig> employeeFmsFolders = allFmsFolders.stream()
                .filter(folder -> finalPermissions.contains("FMS:" + folder.getId()))
                .collect(java.util.stream.Collectors.toList());

        context.put("employeeName", employee.getName());
        context.put("permissions", finalPermissions);
        context.put("employee", employee);
        context.put("fmsFolders", employeeFmsFolders);

        return context;
    }

    public java.util.List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }
}
