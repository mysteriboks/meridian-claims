package com.meridian.claims.controller;

import com.meridian.claims.model.TradingPartner;
import com.meridian.claims.service.ServiceException;
import com.meridian.claims.service.TradingPartnerService;
import com.meridian.claims.util.ValidationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin CRUD for the trading-partner master (Phase 13). RoleFilter restricts
 * /admin/** to ADMIN, matching every other Admin screen — no per-method check needed here.
 */
@Controller
@RequestMapping("/admin/trading-partners")
public class TradingPartnerController {

    private final TradingPartnerService tradingPartnerService;

    @Autowired
    public TradingPartnerController(TradingPartnerService tradingPartnerService) {
        this.tradingPartnerService = tradingPartnerService;
    }

    @RequestMapping(method = RequestMethod.GET)
    public String list(Model model) {
        model.addAttribute("partners", tradingPartnerService.listAll());
        return "admin/trading-partners/list";
    }

    @RequestMapping(value = "/new", method = RequestMethod.GET)
    public String showCreate(Model model) {
        return "admin/trading-partners/form";
    }

    @RequestMapping(value = "/new", method = RequestMethod.POST)
    public String create(
            @RequestParam("partnerName") String partnerName,
            @RequestParam("isaQualifier") String isaQualifier,
            @RequestParam("isaId") String isaId,
            @RequestParam("gsId") String gsId,
            @RequestParam(value = "enabledTransactions", required = false) String enabledTransactions,
            @RequestParam("transportType") String transportType,
            @RequestParam(value = "transportHost", required = false) String transportHost,
            @RequestParam(value = "transportPort", required = false) Integer transportPort,
            @RequestParam(value = "transportUsername", required = false) String transportUsername,
            @RequestParam(value = "transportCredentialRef", required = false) String transportCredentialRef,
            @RequestParam("inboundPath") String inboundPath,
            @RequestParam(value = "outboundPath", required = false) String outboundPath,
            @RequestParam(value = "active", defaultValue = "true") boolean active,
            Model model,
            RedirectAttributes redirectAttrs) {
        TradingPartner p = buildFromForm(null, partnerName, isaQualifier, isaId, gsId, enabledTransactions,
            transportType, transportHost, transportPort, transportUsername, transportCredentialRef,
            inboundPath, outboundPath, active);
        String err = validateFields(p);
        if (err != null) {
            model.addAttribute("error", err);
            model.addAttribute("partner", p);
            return "admin/trading-partners/form";
        }
        try {
            tradingPartnerService.create(p);
            redirectAttrs.addFlashAttribute("success", "Trading partner " + p.getPartnerName() + " created.");
            return "redirect:/admin/trading-partners";
        } catch (ServiceException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("partner", p);
            return "admin/trading-partners/form";
        }
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.GET)
    public String showEdit(@PathVariable("id") int id, Model model) {
        model.addAttribute("partner", tradingPartnerService.findById(id));
        return "admin/trading-partners/form";
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.POST)
    public String update(
            @PathVariable("id") int id,
            @RequestParam("partnerName") String partnerName,
            @RequestParam("isaQualifier") String isaQualifier,
            @RequestParam("isaId") String isaId,
            @RequestParam("gsId") String gsId,
            @RequestParam(value = "enabledTransactions", required = false) String enabledTransactions,
            @RequestParam("transportType") String transportType,
            @RequestParam(value = "transportHost", required = false) String transportHost,
            @RequestParam(value = "transportPort", required = false) Integer transportPort,
            @RequestParam(value = "transportUsername", required = false) String transportUsername,
            @RequestParam(value = "transportCredentialRef", required = false) String transportCredentialRef,
            @RequestParam("inboundPath") String inboundPath,
            @RequestParam(value = "outboundPath", required = false) String outboundPath,
            @RequestParam(value = "active", defaultValue = "true") boolean active,
            Model model,
            RedirectAttributes redirectAttrs) {
        TradingPartner p = buildFromForm(id, partnerName, isaQualifier, isaId, gsId, enabledTransactions,
            transportType, transportHost, transportPort, transportUsername, transportCredentialRef,
            inboundPath, outboundPath, active);
        String err = validateFields(p);
        if (err != null) {
            model.addAttribute("error", err);
            model.addAttribute("partner", p);
            return "admin/trading-partners/form";
        }
        try {
            tradingPartnerService.update(p);
            redirectAttrs.addFlashAttribute("success", "Trading partner updated.");
            return "redirect:/admin/trading-partners";
        } catch (ServiceException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("partner", p);
            return "admin/trading-partners/form";
        }
    }

    @RequestMapping(value = "/{id}/deactivate", method = RequestMethod.POST)
    public String deactivate(@PathVariable("id") int id, RedirectAttributes redirectAttrs) {
        tradingPartnerService.deactivate(id);
        redirectAttrs.addFlashAttribute("success", "Trading partner deactivated.");
        return "redirect:/admin/trading-partners";
    }

    private TradingPartner buildFromForm(Integer id, String partnerName, String isaQualifier, String isaId,
                                          String gsId, String enabledTransactions, String transportType,
                                          String transportHost, Integer transportPort, String transportUsername,
                                          String transportCredentialRef, String inboundPath, String outboundPath,
                                          boolean active) {
        TradingPartner p = new TradingPartner();
        if (id != null) p.setId(id);
        p.setPartnerName(trim(partnerName));
        p.setIsaQualifier(trim(isaQualifier));
        p.setIsaId(trim(isaId));
        p.setGsId(trim(gsId));
        p.setEnabledTransactions(trim(enabledTransactions));
        p.setTransportType(trim(transportType));
        p.setTransportHost(trim(transportHost));
        p.setTransportPort(transportPort);
        p.setTransportUsername(trim(transportUsername));
        p.setTransportCredentialRef(trim(transportCredentialRef));
        p.setInboundPath(trim(inboundPath));
        p.setOutboundPath(trim(outboundPath));
        p.setActive(active);
        return p;
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    private String validateFields(TradingPartner p) {
        String nameErr = ValidationUtil.validateRequiredText(p.getPartnerName(), "Partner name", 100);
        if (nameErr != null) return nameErr;
        if (p.getIsaQualifier() == null || p.getIsaQualifier().length() > 2) {
            return "ISA qualifier must be 1-2 characters";
        }
        if (p.getIsaId() == null || p.getIsaId().isEmpty() || p.getIsaId().length() > 15) {
            return "ISA ID is required (max 15 characters)";
        }
        if (p.getGsId() == null || p.getGsId().isEmpty() || p.getGsId().length() > 15) {
            return "GS ID is required (max 15 characters)";
        }
        return null;
    }
}
