<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Record Payment — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <h1>Record Payment</h1>
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <table class="data-table" style="max-width:500px;margin-bottom:24px">
        <tr><td style="color:#6b7888">Payment ID</td><td><c:out value="${payment.id}"/></td></tr>
        <tr><td style="color:#6b7888">Claim ID</td><td><a href="${pageContext.request.contextPath}/claims/${payment.claimId}"><c:out value="${payment.claimId}"/></a></td></tr>
        <tr><td style="color:#6b7888">Billed Total</td><td>$<c:out value="${payment.billedTotal}"/></td></tr>
        <tr><td style="color:#6b7888">Allowed Total</td><td>$<c:out value="${payment.allowedTotal}"/></td></tr>
        <tr><td style="color:#6b7888">Plan Paid Total</td><td>$<c:out value="${payment.planPaidTotal}"/></td></tr>
        <tr><td style="color:#6b7888">Member Responsibility</td><td>$<c:out value="${payment.memberResponsibility}"/></td></tr>
        <tr><td style="color:#6b7888">Status</td><td><c:out value="${payment.status}"/></td></tr>
    </table>

    <h2 class="section-heading">Record Payment</h2>
    <form method="post" action="${pageContext.request.contextPath}/finance/payments/${payment.id}/mark-paid" style="display:flex;flex-direction:column;gap:10px;max-width:400px">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <div>
            <label>Reference Number *</label><br>
            <input type="text" name="referenceNumber" required style="width:100%">
        </div>
        <div>
            <label>Payment Date * (yyyy-MM-dd)</label><br>
            <input type="date" name="paymentDate" required style="width:100%">
        </div>
        <div>
            <label>Amount Paid * (partial allowed)</label><br>
            <input type="text" name="amountPaid" placeholder="${payment.planPaidTotal}" required style="width:100%">
        </div>
        <button type="submit" class="btn btn-primary" onclick="return confirm('Record this payment?')">Record Payment</button>
    </form>

    <p style="margin-top:20px"><a href="${pageContext.request.contextPath}/finance/payments">&larr; Back to Payment Queue</a></p>
</div>
</body>
</html>
