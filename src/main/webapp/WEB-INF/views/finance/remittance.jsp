<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Remittance — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <h1>Remittance Batches</h1>
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <h2 class="section-heading">Generate New Batch</h2>
    <form method="post" action="${pageContext.request.contextPath}/finance/remittance/generate" style="display:flex;gap:8px;align-items:flex-end;flex-wrap:wrap;margin-bottom:24px">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <div>
            <label style="font-size:12px;color:#6b7888">Payment IDs (comma-separated)</label><br>
            <input type="text" name="paymentIds" placeholder="1,2,3" style="width:220px" required>
        </div>
        <div>
            <label style="font-size:12px;color:#6b7888">Payment Date</label><br>
            <input type="date" name="paymentDate" required style="width:150px">
        </div>
        <button type="submit" class="btn btn-primary">Generate Batch</button>
    </form>

    <h2 class="section-heading">Pending Payments</h2>
    <table class="data-table" style="margin-bottom:24px">
        <thead><tr><th>Payment ID</th><th>Claim ID</th><th>Plan Paid</th><th>Status</th></tr></thead>
        <tbody>
        <c:forEach var="p" items="${pendingPayments}">
            <tr>
                <td><c:out value="${p.id}"/></td>
                <td><a href="${pageContext.request.contextPath}/claims/${p.claimId}"><c:out value="${p.claimId}"/></a></td>
                <td>$<c:out value="${p.planPaidTotal}"/></td>
                <td><c:out value="${p.status}"/></td>
            </tr>
        </c:forEach>
        <c:if test="${empty pendingPayments}">
            <tr><td colspan="4" style="text-align:center;color:#9aa6b4;padding:16px">No pending payments.</td></tr>
        </c:if>
        </tbody>
    </table>

    <h2 class="section-heading">Generated Batches</h2>
    <table class="data-table">
        <thead><tr><th>Batch #</th><th>Payment Date</th><th>Total Paid</th><th>Status</th><th>Generated</th><th>Actions</th></tr></thead>
        <tbody>
        <c:forEach var="b" items="${batches}">
            <tr>
                <td><c:out value="${b.id}"/></td>
                <td><c:out value="${b.paymentDate}"/></td>
                <td>$<c:out value="${b.totalPaid}"/></td>
                <td><span class="badge badge-${b.status == 'SENT' ? 'active' : 'pending'}"><c:out value="${b.status}"/></span></td>
                <td><fmt:formatDate value="${b.generatedAt}" pattern="yyyy-MM-dd HH:mm"/></td>
                <td><a href="${pageContext.request.contextPath}/finance/remittance/${b.id}" class="btn-link">View</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty batches}">
            <tr><td colspan="6" style="text-align:center;color:#9aa6b4;padding:16px">No batches yet.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>
</body>
</html>
