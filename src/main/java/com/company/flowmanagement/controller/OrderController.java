package com.company.flowmanagement.controller;

import com.company.flowmanagement.model.O2DConfig;
import com.company.flowmanagement.model.OrderEntry;
import com.company.flowmanagement.model.PlanningEntry;
import com.company.flowmanagement.repository.O2DConfigRepository;
import com.company.flowmanagement.repository.OrderEntryRepository;
import com.company.flowmanagement.repository.PlanningEntryRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/order")
public class OrderController {

    private final O2DConfigRepository repository;
    private final OrderEntryRepository orderEntryRepository;
    private final PlanningEntryRepository planningEntryRepository;

    public OrderController(O2DConfigRepository repository,
            OrderEntryRepository orderEntryRepository,
            PlanningEntryRepository planningEntryRepository) {
        this.repository = repository;
        this.orderEntryRepository = orderEntryRepository;
        this.planningEntryRepository = planningEntryRepository;
    }

    @GetMapping("/dashboard")
    public String orderDashboard(@RequestParam(name = "folderId", required = false) String folderId,
            @RequestParam(name = "entryId", required = false) String entryId,
            @RequestParam(name = "planOrderId", required = false) String planOrderId,
            @RequestParam(name = "planStart", required = false) String planStart,
            @RequestParam(name = "planning", required = false) Boolean planning,
            @RequestParam(name = "saved", required = false) Boolean saved,
            Model model) {
        O2DConfig config = null;
        if (folderId != null && !folderId.isBlank()) {
            config = repository.findById(folderId).orElse(null);
        }
        if (config == null) {
            List<O2DConfig> all = repository.findAll();
            if (!all.isEmpty()) {
                config = all.get(0);
            }
        }

        if (config != null) {
            model.addAttribute("orderDetails", config.getOrderDetails());
            model.addAttribute("selectedFolderId", config.getId());
            model.addAttribute("configOrderId", config.getOrderId());
            model.addAttribute("configCustomerName", config.getCustomerName());
            model.addAttribute("configCompanyName", config.getCompanyName());
            model.addAttribute("configRawMaterial", config.getRawMaterial());
            model.addAttribute("configQuantity", config.getQuantity());
            model.addAttribute("configCDD", config.getCDD());
            model.addAttribute("configMPD", config.getMPD());
            model.addAttribute("configStartDate", config.getStartDate());
            model.addAttribute("processDetails", config.getProcessDetails());

            List<String> responsibleOptions = new ArrayList<>();
            for (var step : config.getProcessDetails()) {
                if (step.getResponsiblePerson() != null && !step.getResponsiblePerson().isBlank()) {
                    String value = step.getResponsiblePerson().trim();
                    if (!responsibleOptions.contains(value)) {
                        responsibleOptions.add(value);
                    }
                }
            }
            if (responsibleOptions.isEmpty()) {
                responsibleOptions.add("Employee");
            }
            model.addAttribute("responsibleOptions", responsibleOptions);

            List<OrderEntry> entries = orderEntryRepository.findByFolderIdOrderByCreatedAtDesc(config.getId());
            model.addAttribute("orderEntries", entries);
            model.addAttribute("totalOrders", entries.size());
            model.addAttribute("completedOrders", 0);
            model.addAttribute("pendingOrders", entries.size());

            List<Map<String, Object>> entryRows = new ArrayList<>();
            int sr = 1;
            List<String> pendingOrderIds = new ArrayList<>();
            for (OrderEntry entry : entries) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sr", sr++);
                row.put("entryId", entry.getId());
                row.put("orderId", entry.getOrderId());
                List<String> values = new ArrayList<>();
                if (config.getOrderDetails() != null) {
                    for (String detail : config.getOrderDetails()) {
                        String normalizedDetail = normalizeKey(detail);
                        String value = "";
                        if (entry.getFields() != null) {
                            if (entry.getFields().containsKey(normalizedDetail)) {
                                value = entry.getFields().get(normalizedDetail);
                            } else {
                                for (Map.Entry<String, String> field : entry.getFields().entrySet()) {
                                    if (normalizeKey(field.getKey()).equals(normalizedDetail)) {
                                        value = field.getValue();
                                        break;
                                    }
                                }
                            }
                        }
                        values.add(value == null ? "" : value);
                    }
                }
                row.put("values", values);
                String planningStatus = findFieldValue(entry.getFields(), "Planning Status", "planning_status");
                if (planningStatus == null || planningStatus.isBlank() || "-".equals(planningStatus)) {
                    planningStatus = "Pending";
                }
                row.put("planningStatus", planningStatus);
                if ("Pending".equalsIgnoreCase(planningStatus) && entry.getOrderId() != null
                        && !entry.getOrderId().isBlank()) {
                    String orderId = entry.getOrderId().trim();
                    if (!pendingOrderIds.contains(orderId)) {
                        pendingOrderIds.add(orderId);
                    }
                }
                entryRows.add(row);
            }
            model.addAttribute("orderEntryRows", entryRows);
            model.addAttribute("pendingOrderIds", pendingOrderIds);
            if (entryId != null && !entryId.isBlank()) {
                OrderEntry selected = orderEntryRepository.findById(entryId).orElse(null);
                if (selected != null) {
                    model.addAttribute("selectedEntry", selected);
                    model.addAttribute("selectedEntryFields", selected.getFields());
                }
            } else if (planOrderId != null && !planOrderId.isBlank()) {
                OrderEntry selected = orderEntryRepository
                        .findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(config.getId(), planOrderId.trim());
                if (selected != null) {
                    model.addAttribute("selectedEntry", selected);
                    model.addAttribute("selectedEntryFields", selected.getFields());
                }
            } else if (!entries.isEmpty()) {
                OrderEntry latest = entries.get(0);
                model.addAttribute("selectedEntry", latest);
                model.addAttribute("selectedEntryFields", latest.getFields());
            }

            List<PlanningEntry> planningEntries = planningEntryRepository
                    .findByFolderIdOrderByCreatedAtAsc(config.getId());
            List<Map<String, Object>> planningBlocks = new ArrayList<>();
            for (PlanningEntry planningEntry : planningEntries) {
                Map<String, Object> block = new LinkedHashMap<>();
                String planningOrderId = planningEntry.getOrderId();
                String planningStart = planningEntry.getStartDate();
                block.put("orderId", planningOrderId);
                block.put("startDate", planningStart);

                OrderEntry entryForPlan = null;
                if (planningOrderId != null && !planningOrderId.isBlank()) {
                    entryForPlan = orderEntryRepository
                            .findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(config.getId(), planningOrderId.trim());
                }
                Map<String, String> entryFields = entryForPlan != null ? entryForPlan.getFields() : null;
                block.put("customerName", findFieldValue(entryFields, "Customer Name", "customer_name"));
                block.put("companyName", findFieldValue(entryFields, "Company Name", "company_name"));

                List<Map<String, String>> rows = new ArrayList<>();
                if (planningStart != null && !planningStart.isBlank()) {
                    try {
                        var startDate = java.time.LocalDate.parse(planningStart.trim());
                        int planningSr = 1;
                        for (var step : config.getProcessDetails()) {
                            var row = new LinkedHashMap<String, String>();
                            row.put("sr", String.valueOf(planningSr++));
                            row.put("stepProcess", step.getStepProcess());
                            row.put("responsiblePerson", step.getResponsiblePerson());
                            row.put("targetType", step.getTargetType());
                            row.put("days", step.getDays() == null ? "" : String.valueOf(step.getDays()));
                            if (step.getDays() != null) {
                                row.put("targetDate", startDate.plusDays(step.getDays()).toString());
                            } else {
                                row.put("targetDate", "-");
                            }
                            row.put("status", "On Track");
                            rows.add(row);
                        }
                    } catch (Exception ignored) {
                        rows = List.of();
                    }
                }
                block.put("rows", rows);
                planningBlocks.add(block);
            }
            model.addAttribute("planningBlocks", planningBlocks);
        }
        model.addAttribute("saved", saved != null && saved);
        return "order-dashboard";
    }

    @GetMapping("/entry")
    @ResponseBody
    public OrderEntry fetchEntry(@RequestParam("folderId") String folderId,
            @RequestParam("orderId") String orderId) {
        if (folderId == null || folderId.isBlank() || orderId == null || orderId.isBlank()) {
            return null;
        }
        return orderEntryRepository.findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(folderId.trim(), orderId.trim());
    }

    @PostMapping("/entry")
    public String createOrderEntry(@RequestParam("folderId") String folderId,
            @RequestParam("orderId") String orderId,
            @RequestParam Map<String, String> params) {
        if (folderId == null || folderId.isBlank()) {
            return "redirect:/order/dashboard";
        }
        Map<String, String> fields = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith("field_")) {
                fields.put(key.substring("field_".length()), value);
            }
        });

        OrderEntry entry = new OrderEntry();
        entry.setFolderId(folderId.trim());
        entry.setOrderId(orderId == null ? "" : orderId.trim());
        entry.setFields(fields);
        entry.setCreatedAt(Instant.now());
        orderEntryRepository.save(entry);

        return "redirect:/order/dashboard?folderId=" + folderId + "&entryId=" + entry.getId() + "&saved=true";
    }

    @PostMapping("/planning")
    public String submitPlanning(@RequestParam("folderId") String folderId,
            @RequestParam("orderId") String orderId,
            @RequestParam(name = "startDate", required = false) String startDate) {

        if (folderId == null || folderId.isBlank()) {
            return "redirect:/order/dashboard";
        }

        String safeFolder = folderId.trim();
        String safeOrder = orderId == null ? "" : orderId.trim();
        String safeStart = startDate == null ? "" : startDate.trim();

        // 1️⃣ Save Planning Entry
        if (!safeStart.isBlank()) {
            PlanningEntry planningEntry = new PlanningEntry();
            planningEntry.setFolderId(safeFolder);
            planningEntry.setOrderId(safeOrder);
            planningEntry.setStartDate(safeStart);
            planningEntry.setCreatedAt(Instant.now());
            planningEntryRepository.save(planningEntry);
        }

        // 2️⃣ UPDATE ORDER STATUS → PLANNED ⭐ IMPORTANT
        if (!safeOrder.isBlank()) {
            OrderEntry entry = orderEntryRepository
                    .findFirstByFolderIdAndOrderIdOrderByCreatedAtDesc(safeFolder, safeOrder);

            if (entry != null) {
                Map<String, String> fields = entry.getFields();
                if (fields == null) {
                    fields = new LinkedHashMap<>();
                }

                fields.put("planning_status", "Planned"); // 🔥 main line
                entry.setFields(fields);

                orderEntryRepository.save(entry);
            }
        }

        // 3️⃣ Redirect dashboard
        return "redirect:/order/dashboard?folderId=" + safeFolder;
    }

    @PostMapping("/planning-status")
    public String updatePlanningStatus(@RequestParam("entryId") String entryId,
            @RequestParam("planningStatus") String planningStatus,
            @RequestParam("folderId") String folderId) {
        if (entryId == null || entryId.isBlank()) {
            return "redirect:/order/dashboard?folderId=" + (folderId == null ? "" : folderId.trim());
        }
        OrderEntry entry = orderEntryRepository.findById(entryId.trim()).orElse(null);
        if (entry != null) {
            Map<String, String> fields = entry.getFields();
            if (fields == null) {
                fields = new LinkedHashMap<>();
            }
            String statusValue = planningStatus == null ? "" : planningStatus.trim();
            fields.put("planning_status", statusValue);
            entry.setFields(fields);
            orderEntryRepository.save(entry);
        }
        String safeFolder = folderId == null ? "" : folderId.trim();
        return "redirect:/order/dashboard?folderId=" + safeFolder;
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase();
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return normalized;
    }

    private static String findFieldValue(Map<String, String> fields, String label, String defaultKey) {
        if (fields == null || fields.isEmpty()) {
            return "-";
        }
        if (defaultKey != null && fields.containsKey(defaultKey)) {
            String val = fields.get(defaultKey);
            return (val == null || val.isBlank()) ? "-" : val;
        }
        String normalizedLabel = normalizeKey(label);
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (normalizeKey(entry.getKey()).equals(normalizedLabel)) {
                String val = entry.getValue();
                return (val == null || val.isBlank()) ? "-" : val;
            }
        }
        return "-";
    }
}
