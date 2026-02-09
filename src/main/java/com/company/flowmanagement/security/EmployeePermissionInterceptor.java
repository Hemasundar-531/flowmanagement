package com.company.flowmanagement.security;

import com.company.flowmanagement.model.Employee;
import com.company.flowmanagement.repository.EmployeeRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Intercepts employee routes and checks permissions before allowing access.
 */
@Component
public class EmployeePermissionInterceptor implements HandlerInterceptor {

    private final EmployeeRepository employeeRepository;

    public EmployeePermissionInterceptor(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // Allow access to the main dashboard and login pages
        String requestURI = request.getRequestURI();
        if (requestURI.equals("/employee/dashboard") || requestURI.equals("/login")) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            response.sendRedirect("/login");
            return false;
        }

        String username = authentication.getName();
        Optional<Employee> employeeOpt = employeeRepository.findByName(username);

        if (employeeOpt.isEmpty()) {
            // Employee not found, deny access
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Employee profile not found.");
            return false;
        }

        Employee employee = employeeOpt.get();
        List<String> permissions = employee.getPermissions();
        if (permissions == null) {
            permissions = new ArrayList<>();
        }

        // Check permission for the requested URL
        if (!checkPermission(requestURI, permissions)) {
            System.out.println("🚫 ACCESS DENIED for " + username + " to " + requestURI);
            System.out.println("  Permissions: " + permissions);
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Access denied: You do not have permission to access this page.");
            return false;
        }

        System.out.println("✅ ACCESS GRANTED for " + username + " to " + requestURI);
        return true;
    }

    /**
     * Check if the employee has permission to access the given URL.
     * Permissions format:
     * - "ORDER_ENTRY" for order entry
     * - "TASK_MANAGER" for task manager
     * - "FMS:<folderId>" for specific FMS folders
     */
    private boolean checkPermission(String requestURI, List<String> permissions) {
        // Order Entry routes
        if (requestURI.startsWith("/employee/order-entry")) {
            return permissions.contains("ORDER_ENTRY");
        }

        // Task Manager routes
        if (requestURI.startsWith("/employee/task-manager")) {
            return permissions.contains("TASK_MANAGER");
        }

        // FMS routes - check for specific folder access
        if (requestURI.startsWith("/employee/fms")) {
            // If accessing /employee/fms (main page), check if user has ANY FMS permission
            if (requestURI.equals("/employee/fms")) {
                return permissions.stream().anyMatch(p -> p.startsWith("FMS:"));
            }

            // For specific folder routes like /employee/fms/folder/<id>
            // Extract folder ID from URL and check permission
            // For now, legacy support for hardcoded folder1 and folder2
            if (requestURI.startsWith("/employee/fms/folder1")) {
                return permissions.contains("FMS_FOLDER1") ||
                        permissions.stream().anyMatch(p -> p.startsWith("FMS:"));
            }
            if (requestURI.startsWith("/employee/fms/folder2")) {
                return permissions.contains("FMS_FOLDER2") ||
                        permissions.stream().anyMatch(p -> p.startsWith("FMS:"));
            }

            // Generic FMS folder access - check if they have any FMS permission
            return permissions.stream().anyMatch(p -> p.startsWith("FMS:"));
        }

        // Default: deny access to unknown routes
        return false;
    }
}
