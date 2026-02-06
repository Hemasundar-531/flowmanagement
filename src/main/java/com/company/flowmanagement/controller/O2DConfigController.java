package com.company.flowmanagement.controller;

import com.company.flowmanagement.model.O2DConfig;
import com.company.flowmanagement.model.ProcessStep;
import com.company.flowmanagement.model.User;
import com.company.flowmanagement.repository.O2DConfigRepository;
import com.company.flowmanagement.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Controller
@RequestMapping("/admin")
public class O2DConfigController {

    private final O2DConfigRepository repository;
    private final UserRepository userRepository;

    public O2DConfigController(O2DConfigRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @GetMapping("/fms-list")
    public String listFolders(Model model) {
        model.addAttribute("folders", repository.findAll());
        return "fms-list";
    }

    @PostMapping("/fms-list")
    public String createFolder(
            @RequestParam("name") String name,
            @RequestParam(value = "orderId", required = false) String orderId,
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "companyName", required = false) String companyName,
            @RequestParam(value = "rawMaterial", required = false) String rawMaterial,
            @RequestParam(value = "quantity", required = false) Integer quantity,
            @RequestParam(value = "cdd", required = false) String cdd,
            @RequestParam(value = "mpd", required = false) String mpd,
            @RequestParam(value = "startDate", required = false) String startDate
    ) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "redirect:/admin/fms-list";
        }

        O2DConfig config = new O2DConfig();
        config.setName(trimmed);
        config.setConfigured(false);
        config.setOrderId(safeTrim(orderId));
        config.setCustomerName(safeTrim(customerName));
        config.setCompanyName(safeTrim(companyName));
        config.setRawMaterial(safeTrim(rawMaterial));
        config.setQuantity(quantity);
        config.setCDD(safeTrim(cdd));
        config.setMPD(safeTrim(mpd));
        config.setStartDate(safeTrim(startDate));
        repository.save(config);

        return "redirect:/admin/fms-process?folderId=" + config.getId();
    }

    @GetMapping("/fms-process")
    public String viewO2D(@RequestParam("folderId") String folderId,
                          @RequestParam(name = "edit", required = false, defaultValue = "false") boolean edit,
                          Model model) {
        O2DConfig config = repository.findById(folderId).orElse(null);
        if (config == null) {
            return "redirect:/admin/fms-list";
        }

        boolean hasData = config.isConfigured()
                || (config.getOrderDetails() != null && !config.getOrderDetails().isEmpty())
                || (config.getProcessDetails() != null && !config.getProcessDetails().isEmpty());

        model.addAttribute("folderId", folderId);
        model.addAttribute("folderName", config.getName());
        model.addAttribute("hasData", hasData);
        model.addAttribute("editMode", edit);
        model.addAttribute("config", config);
        return "fms-process";
    }

    @PostMapping("/fms-process-steps")
    public String saveOrderDetails(
            @RequestParam("folderId") String folderId,
            @RequestParam(name = "orderDetails", required = false) List<String> orderDetails,
            Model model,
            HttpSession session
    ) {
        List<String> cleanedOrderDetails = cleanOrderDetails(orderDetails);
        session.setAttribute("orderDetailsDraft", cleanedOrderDetails);
        session.setAttribute("folderIdDraft", folderId);

        return "redirect:/admin/fms-process-steps?folderId=" + folderId + "&edit=true";
    }

    @GetMapping("/fms-process-steps")
    public String viewProcessSteps(@RequestParam("folderId") String folderId,
                                   @RequestParam(name = "edit", required = false, defaultValue = "false") boolean edit,
                                   HttpSession session,
                                   Model model) {
        O2DConfig config = repository.findById(folderId).orElse(null);
        if (config == null) {
            return "redirect:/admin/fms-list";
        }
        if (config.isConfigured() && !edit) {
            return "redirect:/admin/fms-process?folderId=" + folderId;
        }

        if (session.getAttribute("orderDetailsDraft") == null) {
            if (edit && config.getOrderDetails() != null) {
                session.setAttribute("orderDetailsDraft", config.getOrderDetails());
                session.setAttribute("folderIdDraft", folderId);
            }
        }

        if (session.getAttribute("orderDetailsDraft") == null) {
            return "redirect:/admin/fms-process?folderId=" + folderId;
        }

        model.addAttribute("folderId", folderId);
        model.addAttribute("folderName", config.getName());
        model.addAttribute("editMode", edit);
        model.addAttribute("config", config);
        model.addAttribute("employees", userRepository.findByRole("EMPLOYEE"));
        return "fms-process-steps";
    }

    @PostMapping("/fms-process")
    public String saveO2D(
            @RequestParam("folderId") String folderId,
            @RequestParam(name = "stepProcess", required = false) List<String> stepProcess,
            @RequestParam(name = "responsiblePerson", required = false) List<String> responsiblePerson,
            @RequestParam(name = "targetType", required = false) List<String> targetType,
            @RequestParam(name = "days", required = false) List<String> days,
            HttpSession session
    ) {
        List<String> cleanedOrderDetails = new ArrayList<>();
        Object draftId = session.getAttribute("folderIdDraft");
        if (folderId.equals(draftId)) {
            Object draft = session.getAttribute("orderDetailsDraft");
            if (draft instanceof List) {
                cleanedOrderDetails = (List<String>) draft;
            }
        }

        List<ProcessStep> cleanedSteps = new ArrayList<>();
        if (stepProcess != null) {
            int size = stepProcess.size();

            IntStream.range(0, size).forEach(i -> {
                String step = valueAt(stepProcess, i);
                String person = valueAt(responsiblePerson, i);
                String type = valueAt(targetType, i);
                String dayValue = valueAt(days, i);

                if (step.isEmpty() && person.isEmpty() && type.isEmpty() && dayValue.isEmpty()) {
                    return;
                }

                ProcessStep processStep = new ProcessStep();
                processStep.setStepProcess(step);
                processStep.setResponsiblePerson(person);
                processStep.setTargetType(type);
                processStep.setDays(parseDays(dayValue));
                cleanedSteps.add(processStep);
            });
        }

        O2DConfig config = repository.findById(folderId).orElse(null);
        if (config == null) {
            return "redirect:/admin/fms-list";
        }
        config.setOrderDetails(cleanedOrderDetails);
        config.setProcessDetails(cleanedSteps);
        config.setConfigured(true);
        repository.save(config);

        session.removeAttribute("orderDetailsDraft");
        session.removeAttribute("folderIdDraft");

        return "redirect:/admin/fms-list";
    }

    @PostMapping("/fms-list/delete")
    public String deleteFolder(@RequestParam("id") String id) {
        if (id != null && !id.isBlank()) {
            repository.deleteById(id);
        }
        return "redirect:/admin/fms-list";
    }

    private List<String> cleanOrderDetails(List<String> orderDetails) {
        List<String> cleaned = new ArrayList<>();
        if (orderDetails == null) {
            return cleaned;
        }
        for (String value : orderDetails) {
            if (value != null && !value.trim().isEmpty()) {
                cleaned.add(value.trim());
            }
        }
        return cleaned;
    }

    private Integer parseDays(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String valueAt(List<String> values, int index) {
        if (values == null || index >= values.size()) {
            return "";
        }
        String value = values.get(index);
        return value == null ? "" : value.trim();
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }
}
