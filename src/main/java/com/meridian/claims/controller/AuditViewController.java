package com.meridian.claims.controller;

import com.meridian.claims.model.AuditLogEntry;
import com.meridian.claims.model.User;
import com.meridian.claims.model.UserRole;
import com.meridian.claims.service.AuditService;
import com.meridian.claims.service.AuthenticationService;
import com.meridian.claims.util.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

/**
 * Read-only Admin audit-log viewer at /admin/audit.
 * Searchable by username, event type, entity type, and date range.
 * ADMIN role required (RoleFilter enforces /admin/** access).
 */
@Controller
@RequestMapping("/admin/audit")
public class AuditViewController {

    private final AuditService auditService;
    private final AuthenticationService authService;
    private final com.meridian.claims.util.PaginationConfig paginationConfig;

    @Autowired
    public AuditViewController(AuditService auditService, AuthenticationService authService,
                              com.meridian.claims.util.PaginationConfig paginationConfig) {
        this.auditService = auditService;
        this.authService = authService;
        this.paginationConfig = paginationConfig;
    }

    @RequestMapping(method = RequestMethod.GET)
    public String view(
            @RequestParam(value = "username",   defaultValue = "") String username,
            @RequestParam(value = "eventType",  defaultValue = "") String eventType,
            @RequestParam(value = "entityType", defaultValue = "") String entityType,
            @RequestParam(value = "from",  required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date from,
            @RequestParam(value = "to",    required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date to,
            @RequestParam(value = "page",  defaultValue = "1") int page,
            HttpServletRequest httpReq,
            RedirectAttributes flash,
            Model model) {
        User user = authService.getCurrentUser(httpReq);
        if (user == null || user.getRole() != UserRole.ADMIN) {
            flash.addFlashAttribute("error", "Only ADMIN may view the audit log");
            return "redirect:/dashboard";
        }

        Page<AuditLogEntry> resultPage = auditService.search(
                username.isEmpty() ? null : username,
                eventType.isEmpty() ? null : eventType,
                entityType.isEmpty() ? null : entityType,
                from, to, page, paginationConfig.getReportPageSize());

        model.addAttribute("auditPage", resultPage);
        model.addAttribute("username", username);
        model.addAttribute("eventType", eventType);
        model.addAttribute("entityType", entityType);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "admin/audit";
    }
}
