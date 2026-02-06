package com.company.flowmanagement.config;

import com.company.flowmanagement.model.User;
import com.company.flowmanagement.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner seedUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            createIfMissing(userRepository, passwordEncoder, "admin", "ADMIN");
            createIfMissing(userRepository, passwordEncoder, "order", "ORDER");
            createIfMissing(userRepository, passwordEncoder, "employee", "EMPLOYEE");
        };
    }

    private void createIfMissing(UserRepository userRepository, PasswordEncoder encoder,
                                 String username, String role) {
        if (userRepository.findByUsername(username) != null) {
            return;
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode("1234567"));
        user.setRole(role);
        userRepository.save(user);
    }
}
