package com.company.flowmanagement.controller;

import com.company.flowmanagement.model.User;
import com.company.flowmanagement.repository.O2DConfigRepository;
import com.company.flowmanagement.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/superadmin")
public class SuperAdminController {

    private final UserRepository userRepository;
    private final O2DConfigRepository o2dConfigRepository;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminController(UserRepository userRepository,
            O2DConfigRepository o2dConfigRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.o2dConfigRepository = o2dConfigRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<User> admins = userRepository.findByRole("ADMIN");
        model.addAttribute("admins", admins);
        model.addAttribute("folders", o2dConfigRepository.findAll());
        return "superadmin-dashboard";
    }

    @PostMapping("/create-admin")
    public String createAdmin(@RequestParam("companyName") String companyName,
            @RequestParam("customerName") String customerName,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("companyLogo") MultipartFile companyLogo,
            Model model) {

        try {
            // Check if username/email already exists (using email as username for
            // simplicity or separate?)
            // The prompt asks for Email, but User model has Username. I'll use Email as
            // Username.
            if (userRepository.findByUsername(email) != null) {
                model.addAttribute("error", "Admin with this email already exists.");
                return dashboard(model);
            }

            User newAdmin = new User();
            newAdmin.setUsername(email); // Using email as username
            newAdmin.setEmail(email);
            newAdmin.setPassword(passwordEncoder.encode(password));
            newAdmin.setRole("ADMIN");
            newAdmin.setCompanyName(companyName);
            newAdmin.setCustomerName(customerName);

            // Handle File Upload
            if (companyLogo != null && !companyLogo.isEmpty()) {
                String uploadDir = "uploads/logos/";
                Path uploadPath = Paths.get(uploadDir);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }
                String fileName = System.currentTimeMillis() + "_" + companyLogo.getOriginalFilename();
                Path filePath = uploadPath.resolve(fileName);
                Files.copy(companyLogo.getInputStream(), filePath);

                // Save relative path for web access
                newAdmin.setCompanyLogo("/uploads/logos/" + fileName);
            }

            userRepository.save(newAdmin);
            model.addAttribute("success", "Admin created successfully!");

        } catch (IOException e) {
            model.addAttribute("error", "Failed to upload logo: " + e.getMessage());
        }

        return dashboard(model);
    }

    @GetMapping("/api/folder-access")
    @ResponseBody
    public List<Map<String, Object>> getFolderAccess(@RequestParam("folderId") String folderId) {
        List<User> admins = userRepository.findByRole("ADMIN");
        List<Map<String, Object>> result = new ArrayList<>();

        for (User admin : admins) {
            Map<String, Object> map = new HashMap<>();
            map.put("username", admin.getUsername());
            map.put("email", admin.getEmail());

            boolean hasAccess = admin.getPermissions() != null
                    && admin.getPermissions().contains("ADMIN_FMS:" + folderId);
            map.put("hasAccess", hasAccess);
            result.add(map);
        }
        return result;
    }

    @PostMapping("/api/folder-access")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> saveFolderAccess(@RequestBody Map<String, Object> body) {
        String folderId = (String) body.get("folderId");
        @SuppressWarnings("unchecked")
        Map<String, Boolean> accessMap = (Map<String, Boolean>) body.get("access");

        if (folderId == null || accessMap == null) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }

        accessMap.forEach((username, shouldHaveAccess) -> {
            User admin = userRepository.findByUsername(username);
            if (admin == null || !"ADMIN".equals(admin.getRole())) {
                return;
            }

            ArrayList<String> perms = admin.getPermissions();
            if (perms == null) {
                perms = new ArrayList<>();
            }

            String permString = "ADMIN_FMS:" + folderId;
            if (Boolean.TRUE.equals(shouldHaveAccess)) {
                if (!perms.contains(permString)) {
                    perms.add(permString);
                }
            } else {
                perms.remove(permString);
            }

            admin.setPermissions(perms);
            userRepository.save(admin);
        });

        return org.springframework.http.ResponseEntity.ok().build();
    }
}
