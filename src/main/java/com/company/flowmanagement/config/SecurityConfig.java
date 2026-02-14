package com.company.flowmanagement.config;

import com.company.flowmanagement.security.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final com.company.flowmanagement.repository.UserRepository userRepository;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
            com.company.flowmanagement.repository.UserRepository userRepository) {
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/img/**", "/static/**", "/uploads/**").permitAll()
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers("/superadmin/**").hasRole("SUPERADMIN")

                        .requestMatchers("/employee/**").hasRole("EMPLOYEE")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(authenticationSuccessHandler())
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll());

        http.userDetailsService(userDetailsService);

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            var authorities = authentication.getAuthorities();
            String targetUrl = "/employee/task-manager";

            if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"))) {
                targetUrl = "/superadmin/dashboard";
            } else if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                targetUrl = "/admin/dashboard";

            } else if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPLOYEE"))) {
                // Dynamic redirect based on permissions
                String username = authentication.getName();
                com.company.flowmanagement.model.User user = userRepository.findByUsername(username);

                targetUrl = "/employee/no-access"; // Default fallback

                if (user != null && user.getPermissions() != null) {
                    if (user.getPermissions().contains("ORDER_ENTRY")) {
                        targetUrl = "/employee/order-entry";
                    } else if (user.getPermissions().contains("TASK_MANAGER")) {
                        targetUrl = "/employee/task-manager";
                    } else if (user.getPermissions().contains("FMS:folder1")) { // Example check, could be more dynamic
                        targetUrl = "/employee/fms";
                    } else if (!user.getPermissions().isEmpty()) {
                        // Fallback to FMS main if they have other permissions
                        targetUrl = "/employee/fms";
                    }
                }
            }

            response.sendRedirect(targetUrl);
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
