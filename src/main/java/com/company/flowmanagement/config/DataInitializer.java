package com.company.flowmanagement.config;

import com.company.flowmanagement.model.Employee;
import com.company.flowmanagement.model.User;
import com.company.flowmanagement.repository.EmployeeRepository;
import com.company.flowmanagement.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner seedUsers(UserRepository userRepository, PasswordEncoder passwordEncoder,
            EmployeeRepository employeeRepository) {
        return args -> {
            createIfMissing(userRepository, passwordEncoder, "admin", "ADMIN");
            createIfMissing(userRepository, passwordEncoder, "order", "ORDER");
            createIfMissing(userRepository, passwordEncoder, "superadmin", "SUPERADMIN");
            createIfMissing(userRepository, passwordEncoder, "employee", "EMPLOYEE");
            createIfMissing(userRepository, passwordEncoder, "E1", "EMPLOYEE");
            createIfMissing(userRepository, passwordEncoder, "E2", "EMPLOYEE");
            createIfMissing(userRepository, passwordEncoder, "E3", "EMPLOYEE");
            createIfMissing(userRepository, passwordEncoder, "E3", "EMPLOYEE");
            seedSampleEmployeeIfEmpty(employeeRepository, userRepository, passwordEncoder);

            // Ensure Employee documents exist for E1, E2, E3, and 'employee'
            seedEmployeeIfMissing(employeeRepository, "employee", "General");
            seedEmployeeIfMissing(employeeRepository, "E1", "General");
            seedEmployeeIfMissing(employeeRepository, "E2", "General");
            seedEmployeeIfMissing(employeeRepository, "E3", "General");
        };
    }

    private void seedSampleEmployeeIfEmpty(EmployeeRepository employeeRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        if (employeeRepository.count() > 0)
            return;
        Employee e = new Employee();
        e.setName("Rahul");
        e.setDepartment("Production");
        e.setStatus("Active");
        employeeRepository.save(e);
        if (userRepository.findByUsername("Rahul") == null) {
            User user = new User();
            user.setUsername("Rahul");
            user.setPassword(passwordEncoder.encode("1234567"));
            user.setRole("EMPLOYEE");
            userRepository.save(user);
        }
    }

    private void seedEmployeeIfMissing(EmployeeRepository employeeRepository, String name, String department) {
        if (employeeRepository.findByName(name).isPresent()) {
            return;
        }
        Employee e = new Employee();
        e.setName(name);
        e.setDepartment(department);
        e.setStatus("Active");
        e.setPermissions(new java.util.ArrayList<>());
        employeeRepository.save(e);
    }

    private void createIfMissing(UserRepository userRepository, PasswordEncoder encoder,
            String username, String role) {
        try {
            if (userRepository.findByUsername(username) != null) {
                return;
            }
        } catch (org.springframework.dao.IncorrectResultSizeDataAccessException e) {
            // Found duplicates, delete them and recreate
            userRepository.deleteByUsername(username);
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode("1234567"));
        user.setRole(role);
        userRepository.save(user);
    }
}
