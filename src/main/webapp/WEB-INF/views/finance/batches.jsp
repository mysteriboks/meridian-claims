<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Payment Batches — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <h1>Payment Batches</h1>
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <h2 class="section-heading">Create New Batch</h2>
    <form method="post" action="${pageContext.request.contextPath}/finance/batches/create" style="display:flex;gap:8px;align-items:flex-end;margin-bottom:24px">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <div>
            <label style="font-size:12px;color:#6b7888">Batch Date</label><br>
            <input type="date" name="batchDate" required>
        </div>
        <button type="submit" class="btn btn-primary" onclick="return confirm('Create batch from all PENDING payments?')">Create Batch</button>
    </form>
    <p style="color:#6b7888;font-size:13px">Pending payments: <strong>${pendingPayments.size()}</strong></p>

    <h2 class="section-heading">All Batches</h2>
    <table class="data-table">
        <thead><tr><th>Batch #</th><th>Date</th><th>Total</th><th>Status</th><th>File</th><th>Actions</th></tr></thead>
        <tbody>
        <c:forEach var="b" items="${batches}">
            <tr>
                <td><c:out value="${b.id}"/></td>
                <td><c:out value="${b.batchDate}"/></td>
                <td>$<c:out value="${b.totalAmount}"/></td>
                <td><span class="badge badge-${b.status == 'EXPORTED' ? 'active' : 'pending'}"><c:out value="${b.status}"/></span></td>
                <td><c:out value="${b.fileReference}"/></td>
                <td><a href="${pageContext.request.contextPath}/finance/batches/${b.id}" class="btn-link">View</a></td>
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
