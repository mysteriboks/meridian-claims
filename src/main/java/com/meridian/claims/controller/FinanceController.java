package com.meridian.claims.controller;

import com.meridian.claims.model.EobDocument;
import com.meridian.claims.model.Payment;
import com.meridian.claims.model.PaymentBatch;
import com.meridian.claims.model.RemittanceBatch;
import com.meridian.claims.model.RemittanceBatchItem;
import com.meridian.claims.model.User;
import com.meridian.claims.service.AuthenticationService;
import com.meridian.claims.service.Edi835Generator;
import com.meridian.claims.service.EobService;
import com.meridian.claims.service.MemberExportService;
import com.meridian.claims.service.PaymentBatchService;
import com.meridian.claims.service.PaymentService;
import com.meridian.claims.service.RemittanceService;
import com.meridian.claims.service.ServiceException;
import com.meridian.claims.service.SubrogationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import com.meridian.claims.util.Money;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Finance screens: payment queue, EOB viewer, remittance batch generation.
 * All endpoints require FINANCE or ADMIN role (enforced by RoleFilter on /finance/**).
 */
@Controller
@RequestMapping("/finance")
public class FinanceController {

    @Autowired private PaymentService paymentService;
    @Autowired private PaymentBatchService paymentBatchService;
    @Autowired private EobService eobDocumentService;
    @Autowired private RemittanceService remittanceService;
    @Autowired private Edi835Generator edi835Generator;
    @Autowired private SubrogationService subrogationService;
    @Autowired private MemberExportService memberExportService;
    @Autowired private AuthenticationService authService;

    // -------------------------------------------------------------------------
    // Payments
    // -------------------------------------------------------------------------

    @RequestMapping(value = "/payments", method = RequestMethod.GET)
    public String paymentList(Model model) {
        model.addAttribute("pendingPayments", paymentService.findPending());
        return "finance/payments";
    }

    @RequestMapping(value = "/payments/{id}", method = RequestMethod.GET)
    public String paymentDetail(@PathVariable("id") int id, Model model) {
        Payment payment = paymentService.findById(id);
        if (payment == null) {
            return "redirect:/finance/payments";
        }
        model.addAttribute("payment", payment);
        return "finance/payment-detail";
    }

    @RequestMapping(value = "/payments/{id}/mark-paid", method = RequestMethod.POST)
    public String markPaid(@PathVariable("id") int id,
                           @RequestParam("referenceNumber") String referenceNumber,
                           @RequestParam("paymentDate") String paymentDateStr,
                           @RequestParam("amountPaid") String amountPaidStr,
                           HttpServletRequest httpReq,
                           RedirectAttributes flash) {
        User user = resolveUser(httpReq);
        int userId = user != null ? user.getId() : 0;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date paymentDate = sdf.parse(paymentDateStr);
            BigDecimal amountPaid = Money.of(amountPaidStr.trim());
            paymentService.markPaid(id, referenceNumber, paymentDate, amountPaid, userId);
            flash.addFlashAttribute("success", "Payment recorded");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            flash.addFlashAttribute("error", "Invalid date or amount: " + e.getMessage());
        }
        return "redirect:/finance/payments";
    }

    // -------------------------------------------------------------------------
    // EOB documents
    // -------------------------------------------------------------------------

    @RequestMapping(value = "/eobs", method = RequestMethod.GET)
    public String eobList(@RequestParam(value = "memberId", required = false) Integer memberId,
                          Model model) {
        if (memberId != null) {
            model.addAttribute("eobs", eobDocumentService.findByMemberId(memberId));
            model.addAttribute("memberId", memberId);
        }
        return "finance/eobs";
    }

    @RequestMapping(value = "/eobs/{id}", method = RequestMethod.GET)
    public String eobView(@PathVariable("id") int id, Model model) {
        EobDocument doc = eobDocumentService.findById(id);
        if (doc == null) {
            return "redirect:/finance/eobs";
        }
        model.addAttribute("eob", doc);
        return "finance/eob-view";
    }

    @RequestMapping(value = "/eobs/{id}/mark-mailed", method = RequestMethod.POST)
    public String markMailed(@PathVariable("id") int id,
                             HttpServletRequest httpReq,
                             RedirectAttributes flash) {
        User user = resolveUser(httpReq);
        if (user == null) {
            flash.addFlashAttribute("error", "Not authenticated");
            return "redirect:/finance/eobs";
        }
        try {
            eobDocumentService.markMailed(id, user.getId());
            flash.addFlashAttribute("success", "EOB marked as mailed");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/finance/eobs/" + id;
    }

    // -------------------------------------------------------------------------
    // Remittance batches
    // -------------------------------------------------------------------------

    @RequestMapping(value = "/remittance", method = RequestMethod.GET)
    public String remittanceList(Model model) {
        model.addAttribute("batches", remittanceService.findAllBatches());
        model.addAttribute("pendingPayments", paymentService.findPending());
        return "finance/remittance";
    }

    @RequestMapping(value = "/remittance/generate", method = RequestMethod.POST)
    public String generateBatch(@RequestParam("paymentIds") String paymentIdsStr,
                                @RequestParam("paymentDate") String paymentDateStr,
                                RedirectAttributes flash) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date paymentDate = sdf.parse(paymentDateStr.trim());
            List<Integer> ids = new java.util.ArrayList<Integer>();
            for (String s : paymentIdsStr.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    ids.add(Integer.parseInt(t));
                }
            }
            if (ids.isEmpty()) {
                flash.addFlashAttribute("error", "No payment IDs provided");
                return "redirect:/finance/remittance";
            }
            RemittanceBatch batch = remittanceService.generateBatch(ids, paymentDate);
            flash.addFlashAttribute("success", "Remittance batch #" + batch.getId() + " generated");
            return "redirect:/finance/remittance/" + batch.getId();
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            flash.addFlashAttribute("error", "Invalid input: " + e.getMessage());
        }
        return "redirect:/finance/remittance";
    }

    @RequestMapping(value = "/remittance/{id}", method = RequestMethod.GET)
    public String remittanceDetail(@PathVariable("id") int id, Model model) {
        RemittanceBatch batch = remittanceService.findBatchById(id);
        if (batch == null) {
            return "redirect:/finance/remittance";
        }
        model.addAttribute("batch", batch);
        model.addAttribute("html", remittanceService.buildHtml(id));
        return "finance/remittance-detail";
    }

    @RequestMapping(value = "/remittance/{id}/mark-sent", method = RequestMethod.POST)
    public String markSent(@PathVariable("id") int id, RedirectAttributes flash) {
        try {
            remittanceService.markBatchSent(id);
            flash.addFlashAttribute("success", "Batch marked as sent");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/finance/remittance/" + id;
    }

    @RequestMapping(value = "/remittance/{id}/835", method = RequestMethod.GET)
    public void download835(@PathVariable("id") int id, HttpServletRequest request,
                            HttpServletResponse response) throws IOException {
        RemittanceBatch batch = remittanceService.findBatchById(id);
        if (batch == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        List<RemittanceBatchItem> items = remittanceService.findItemsByBatchId(id);
        String content = edi835Generator.generate(batch, items);
        response.setContentType("application/EDI-X12; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"remittance-" + id + ".835\"");
        response.getWriter().write(content);
    }

    // -------------------------------------------------------------------------
    // Payment batches
    // -------------------------------------------------------------------------

    @RequestMapping(value = "/batches", method = RequestMethod.GET)
    public String batchList(Model model) {
        model.addAttribute("batches", paymentBatchService.findAll());
        model.addAttribute("pendingPayments", paymentService.findPending());
        return "finance/batches";
    }

    @RequestMapping(value = "/batches/create", method = RequestMethod.POST)
    public String createBatch(@RequestParam("batchDate") String batchDateStr,
                               HttpServletRequest req,
                               RedirectAttributes flash) {
        User user = resolveUser(req);
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date batchDate = sdf.parse(batchDateStr.trim());
            PaymentBatch batch = paymentBatchService.createBatch(batchDate, user != null ? user.getId() : 0);
            flash.addFlashAttribute("success", "Batch #" + batch.getId() + " created with " +
                paymentService.findPending().size() + " payments");
            return "redirect:/finance/batches/" + batch.getId();
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            flash.addFlashAttribute("error", "Invalid date: " + e.getMessage());
        }
        return "redirect:/finance/batches";
    }

    @RequestMapping(value = "/batches/{id}", method = RequestMethod.GET)
    public String batchDetail(@PathVariable("id") int id, Model model) {
        PaymentBatch batch = paymentBatchService.findById(id);
        if (batch == null) return "redirect:/finance/batches";
        model.addAttribute("batch", batch);
        return "finance/batch-detail";
    }

    @RequestMapping(value = "/batches/{id}/export", method = RequestMethod.POST)
    public String exportBatch(@PathVariable("id") int id,
                              HttpServletRequest req,
                              RedirectAttributes flash) {
        User user = resolveUser(req);
        try {
            String csv = paymentBatchService.exportCsv(id, user != null ? user.getId() : 0);
            flash.addFlashAttribute("success", "Batch exported successfully");
            flash.addFlashAttribute("csvContent", csv);
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/finance/batches/" + id;
    }

    // -------------------------------------------------------------------------
    // Subrogation
    // -------------------------------------------------------------------------

    @RequestMapping(value = "/subrogation", method = RequestMethod.GET)
    public String subrogationList(Model model) {
        model.addAttribute("cases", subrogationService.findOpen());
        return "finance/subrogation";
    }

    @RequestMapping(value = "/subrogation/{id}", method = RequestMethod.GET)
    public String subrogationDetail(@PathVariable("id") int id, Model model) {
        com.meridian.claims.model.SubrogationCase sc = subrogationService.findById(id);
        if (sc == null) return "redirect:/finance/subrogation";
        model.addAttribute("subroCase", sc);
        return "finance/subrogation-detail";
    }

    @RequestMapping(value = "/subrogation/{id}/recover", method = RequestMethod.POST)
    public String recordRecovery(@PathVariable("id") int id,
                                 @RequestParam("liableParty") String liableParty,
                                 @RequestParam("recoveryAmount") String amountStr,
                                 @RequestParam(value = "notes", defaultValue = "") String notes,
                                 HttpServletRequest req,
                                 RedirectAttributes flash) {
        User user = resolveUser(req);
        try {
            BigDecimal amount = Money.of(amountStr.trim());
            subrogationService.recordRecovery(id, liableParty, amount, notes,
                user != null ? user.getId() : 0);
            flash.addFlashAttribute("success", "Recovery of $" + amount + " recorded");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            flash.addFlashAttribute("error", "Invalid amount: " + e.getMessage());
        }
        return "redirect:/finance/subrogation/" + id;
    }

    // -------------------------------------------------------------------------
    // Member export
    // -------------------------------------------------------------------------

    @RequestMapping(value = "/member-export", method = RequestMethod.GET)
    public String memberExportForm(Model model) {
        return "finance/member-export";
    }

    @RequestMapping(value = "/member-export/claims", method = RequestMethod.GET)
    public void exportMemberClaims(@RequestParam("memberId") int memberId,
                                   javax.servlet.http.HttpServletResponse response) {
        try {
            String csv = memberExportService.exportClaimsCsv(memberId);
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", "attachment; filename=member-" + memberId + "-claims.csv");
            response.getWriter().write(csv);
        } catch (Exception e) {
            try {
                response.sendError(400, e.getMessage());
            } catch (java.io.IOException ignored) {}
        }
    }

    @RequestMapping(value = "/member-export/payments", method = RequestMethod.GET)
    public void exportMemberPayments(@RequestParam("memberId") int memberId,
                                     javax.servlet.http.HttpServletResponse response) {
        try {
            String csv = memberExportService.exportPaymentsCsv(memberId);
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", "attachment; filename=member-" + memberId + "-payments.csv");
            response.getWriter().write(csv);
        } catch (Exception e) {
            try {
                response.sendError(400, e.getMessage());
            } catch (java.io.IOException ignored) {}
        }
    }

    private User resolveUser(HttpServletRequest req) {
        return authService.getCurrentUser(req);
    }
}
