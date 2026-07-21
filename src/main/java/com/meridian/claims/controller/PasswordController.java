package com.meridian.claims.controller;

import com.meridian.claims.model.User;
import com.meridian.claims.service.AuthenticationService;
import com.meridian.claims.service.PasswordService;
import com.meridian.claims.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

@Controller
@RequestMapping("/password")
public class PasswordController {

    private final AuthenticationService authService;
    private final PasswordService passwordService;
    private final UserService userService;

    @Autowired
    public PasswordController(AuthenticationService authService,
                               PasswordService passwordService,
                               UserService userService) {
        this.authService = authService;
        this.passwordService = passwordService;
        this.userService = userService;
    }

    @RequestMapping(value = "/change", method = RequestMethod.GET)
    public String showChange(HttpServletRequest request, Model model) {
        User user = authService.getCurrentUser(request);
        if (user == null) {
            return "redirect:/login";
        }
        if (user.isForceReset()) {
            model.addAttribute("reason", "An administrator has required you to set a new password.");
        } else {
            model.addAttribute("reason", "Your password has expired. Please set a new password.");
        }
        return "password/change";
    }

    @RequestMapping(value = "/change", method = RequestMethod.POST)
    public String processChange(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            HttpServletRequest request,
            Model model) {

        User user = authService.getCurrentUser(request);
        if (user == null) {
            return "redirect:/login";
        }

        if (!passwordService.verify(currentPassword, user.getPasswordHash())) {
            model.addAttribute("error", "Current password is incorrect.");
            return "password/change";
        }

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "New passwords do not match.");
            return "password/change";
        }

        if (!passwordService.meetsComplexity(newPassword)) {
            model.addAttribute("error", passwordService.complexityMessage());
            return "password/change";
        }

        String newHash = passwordService.hash(newPassword);
        userService.updatePasswordHash(user.getId(), newHash, new Date());

        // Refresh user in session
        User refreshed = userService.findById(user.getId());
        request.getSession(false).setAttribute(AuthenticationService.SESSION_USER_KEY, refreshed);

        return "redirect:/dashboard?passwordChanged";
    }
}
