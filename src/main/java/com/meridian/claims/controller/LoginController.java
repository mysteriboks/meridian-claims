package com.meridian.claims.controller;

import com.meridian.claims.service.AuthenticationService;
import com.meridian.claims.service.AuthenticationService.LoginResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

@Controller
public class LoginController {

    private final AuthenticationService authService;

    @Autowired
    public LoginController(AuthenticationService authService) {
        this.authService = authService;
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String showLogin() {
        return "login";
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public String processLogin(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpServletRequest request,
            Model model) {

        LoginResult result = authService.login(username, password, request);

        switch (result) {
            case SUCCESS:
                return "redirect:/dashboard";
            case SUCCESS_PASSWORD_EXPIRED:
            case SUCCESS_FORCE_RESET:
                return "redirect:/password/change";
            case ACCOUNT_LOCKED:
                model.addAttribute("error",
                    "Your account is locked due to too many failed attempts. Try again in 30 minutes or contact an administrator.");
                return "login";
            case ACCOUNT_INACTIVE:
                model.addAttribute("error",
                    "Your account is inactive. Please contact an administrator.");
                return "login";
            case INVALID_CREDENTIALS:
            default:
                model.addAttribute("error", "Invalid username or password.");
                return "login";
        }
    }

    @RequestMapping(value = "/logout", method = RequestMethod.POST)
    public String logout(HttpServletRequest request) {
        authService.logout(request);
        return "redirect:/login?loggedOut";
    }
}
