package com.meridian.claims.controller;

import com.meridian.claims.model.NetworkStatus;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.ProviderType;
import com.meridian.claims.service.ProviderService;
import com.meridian.claims.service.ServiceException;
import com.meridian.claims.util.LogMaskUtil;
import com.meridian.claims.util.Page;
import com.meridian.claims.util.ValidationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/providers")
public class ProviderController {

    private final ProviderService providerService;
    private final com.meridian.claims.util.PaginationConfig paginationConfig;

    @Autowired
    public ProviderController(ProviderService providerService,
                             com.meridian.claims.util.PaginationConfig paginationConfig) {
        this.providerService = providerService;
        this.paginationConfig = paginationConfig;
    }

    @RequestMapping(method = RequestMethod.GET)
    public String list(
            @RequestParam(value = "q", defaultValue = "") String query,
            @RequestParam(value = "page", defaultValue = "1") int pageNumber,
            Model model) {
        Page<Provider> page = providerService.search(query, pageNumber, paginationConfig.getListPageSize());
        model.addAttribute("page", page);
        model.addAttribute("query", query);
        return "providers/list";
    }

    @RequestMapping(value = "/new", method = RequestMethod.GET)
    public String showCreate(Model model) {
        model.addAttribute("providerTypes", ProviderType.values());
        model.addAttribute("networkStatuses", NetworkStatus.values());
        return "providers/form";
    }

    @RequestMapping(value = "/new", method = RequestMethod.POST)
    public String create(
            @RequestParam("npi") String npi,
            @RequestParam("name") String name,
            @RequestParam("providerType") String providerType,
            @RequestParam(value = "specialty", required = false) String specialty,
            @RequestParam("networkStatus") String networkStatus,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "address", required = false) String address,
            Model model,
            RedirectAttributes redirectAttrs) {
        String err = validateProviderFields(npi, name, specialty, phone, address);
        if (err != null) {
            model.addAttribute("error", err);
            model.addAttribute("providerTypes", ProviderType.values());
            model.addAttribute("networkStatuses", NetworkStatus.values());
            return "providers/form";
        }
        try {
            Provider p = providerService.createProvider(npi, name, providerType, specialty, networkStatus, phone, address);
            redirectAttrs.addFlashAttribute("success", "Provider " + p.getNpi() + " created.");
            return "redirect:/providers/" + p.getId();
        } catch (ServiceException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("providerTypes", ProviderType.values());
            model.addAttribute("networkStatuses", NetworkStatus.values());
            return "providers/form";
        }
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public String view(@PathVariable("id") int id, Model model) {
        Provider provider = providerService.findById(id);
        model.addAttribute("provider", provider);
        model.addAttribute("maskedAchAccountNumber", LogMaskUtil.maskMemberNumber(provider.getAchAccountNumber()));
        return "providers/view";
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.GET)
    public String showEdit(@PathVariable("id") int id, Model model) {
        model.addAttribute("provider", providerService.findById(id));
        model.addAttribute("providerTypes", ProviderType.values());
        model.addAttribute("networkStatuses", NetworkStatus.values());
        return "providers/form";
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.POST)
    public String update(
            @PathVariable("id") int id,
            @RequestParam("npi") String npi,
            @RequestParam("name") String name,
            @RequestParam("providerType") String providerType,
            @RequestParam(value = "specialty", required = false) String specialty,
            @RequestParam("networkStatus") String networkStatus,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "address", required = false) String address,
            Model model,
            RedirectAttributes redirectAttrs) {
        String err = validateProviderFields(npi, name, specialty, phone, address);
        if (err != null) {
            model.addAttribute("error", err);
            model.addAttribute("provider", providerService.findById(id));
            model.addAttribute("providerTypes", ProviderType.values());
            model.addAttribute("networkStatuses", NetworkStatus.values());
            return "providers/form";
        }
        try {
            providerService.updateProvider(id, npi, name, providerType, specialty, networkStatus, phone, address);
            redirectAttrs.addFlashAttribute("success", "Provider updated.");
            return "redirect:/providers/" + id;
        } catch (ServiceException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("provider", providerService.findById(id));
            model.addAttribute("providerTypes", ProviderType.values());
            model.addAttribute("networkStatuses", NetworkStatus.values());
            return "providers/form";
        }
    }

    private String validateProviderFields(String npi, String name, String specialty, String phone, String address) {
        String npiError = ValidationUtil.validateNpi(npi);
        if (npiError != null) return npiError;
        String nameError = ValidationUtil.validateRequiredText(name, "Provider name", 255);
        if (nameError != null) return nameError;
        String specialtyError = ValidationUtil.validateOptionalText(specialty, "Specialty", 255);
        if (specialtyError != null) return specialtyError;
        String phoneError = ValidationUtil.validateOptionalText(phone, "Phone", 30);
        if (phoneError != null) return phoneError;
        String addressError = ValidationUtil.validateOptionalText(address, "Address", 500);
        if (addressError != null) return addressError;
        return null;
    }

    @RequestMapping(value = "/{id}/deactivate", method = RequestMethod.POST)
    public String deactivate(@PathVariable("id") int id, RedirectAttributes redirectAttrs) {
        providerService.deactivateProvider(id);
        redirectAttrs.addFlashAttribute("success", "Provider deactivated.");
        return "redirect:/providers";
    }

    @RequestMapping(value = "/{id}/banking", method = RequestMethod.POST)
    public String updateBanking(
            @PathVariable("id") int id,
            @RequestParam(value = "achRoutingNumber", required = false) String routingNumber,
            @RequestParam(value = "achAccountNumber", required = false) String accountNumber,
            @RequestParam(value = "achAccountType", required = false) String accountType,
            RedirectAttributes redirectAttrs) {
        try {
            providerService.updateBankingInfo(id, routingNumber, accountNumber, accountType);
            redirectAttrs.addFlashAttribute("success", "Banking info updated.");
        } catch (ServiceException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/providers/" + id;
    }
}
