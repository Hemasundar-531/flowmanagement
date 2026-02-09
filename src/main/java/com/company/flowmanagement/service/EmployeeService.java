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

    public EmployeeService(EmployeeRepository employeeRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
}
