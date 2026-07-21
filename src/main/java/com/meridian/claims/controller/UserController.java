package com.meridian.claims.controller;

import com.meridian.claims.model.UserRole;
import com.meridian.claims.service.ServiceException;
import com.meridian.claims.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @RequestMapping(method = RequestMethod.GET)
    public String list(Model model) {
        model.addAttribute("users", userService.listAll());
        model.addAttribute("roles", UserRole.values());
        return "admin/users/list";
    }

    @RequestMapping(value = "/new", method = RequestMethod.GET)
    public String showCreate(Model model) {
        model.addAttribute("roles", UserRole.values());
        return "admin/users/form";
    }

    @RequestMapping(value = "/new", method = RequestMethod.POST)
    public String create(
            @RequestParam("username") String username,
            @RequestParam("fullName") String fullName,
            @RequestParam("role") String role,
            @RequestParam("tempPassword") String tempPassword,
            Model model,
            RedirectAttributes redirectAttrs) {
        try {
            userService.createUser(username, fullName, UserRole.valueOf(role), tempPassword);
            redirectAttrs.addFlashAttribute("success", "User created successfully.");
            return "redirect:/admin/users";
        } catch (ServiceException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("roles", UserRole.values());
            return "admin/users/form";
        }
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.GET)
    public String showEdit(@PathVariable("id") int id, Model model) {
        model.addAttribute("user", userService.findById(id));
        model.addAttribute("roles", UserRole.values());
        return "admin/users/edit";
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.POST)
    public String update(
            @PathVariable("id") int id,
            @RequestParam("fullName") String fullName,
            @RequestParam("role") String role,
            Model model,
            RedirectAttributes redirectAttrs) {
        try {
            userService.updateUser(id, fullName, UserRole.valueOf(role));
            redirectAttrs.addFlashAttribute("success", "User updated.");
            return "redirect:/admin/users";
        } catch (ServiceException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("user", userService.findById(id));
            model.addAttribute("roles", UserRole.values());
            return "admin/users/edit";
        }
    }

    @RequestMapping(value = "/{id}/deactivate", method = RequestMethod.POST)
    public String deactivate(@PathVariable("id") int id, RedirectAttributes redirectAttrs) {
        userService.deactivateUser(id);
        redirectAttrs.addFlashAttribute("success", "User deactivated.");
        return "redirect:/admin/users";
    }

    @RequestMapping(value = "/{id}/activate", method = RequestMethod.POST)
    public String activate(@PathVariable("id") int id, RedirectAttributes redirectAttrs) {
        userService.activateUser(id);
        redirectAttrs.addFlashAttribute("success", "User activated.");
        return "redirect:/admin/users";
    }

    @RequestMapping(value = "/{id}/unlock", method = RequestMethod.POST)
    public String unlock(@PathVariable("id") int id, RedirectAttributes redirectAttrs) {
        userService.unlockUser(id);
        redirectAttrs.addFlashAttribute("success", "Account unlocked.");
        return "redirect:/admin/users";
    }

    @RequestMapping(value = "/{id}/force-reset", method = RequestMethod.POST)
    public String forceReset(@PathVariable("id") int id, RedirectAttributes redirectAttrs) {
        userService.forcePasswordReset(id);
        redirectAttrs.addFlashAttribute("success", "User will be required to change password on next login.");
        return "redirect:/admin/users";
    }
}
