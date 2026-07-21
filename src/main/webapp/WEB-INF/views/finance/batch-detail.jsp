<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Batch #<c:out value="${batch.id}"/> — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
        <h1 style="margin:0">Payment Batch #<c:out value="${batch.id}"/></h1>
        <div style="display:flex;gap:8px;align-items:center">
            <span class="badge badge-${batch.status == 'EXPORTED' ? 'active' : 'pending'}"><c:out value="${batch.status}"/></span>
            <c:if test="${batch.status != 'EXPORTED'}">
                <form method="post" action="${pageContext.request.contextPath}/finance/batches/${batch.id}/export" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                    <button type="submit" class="btn btn-primary" onclick="return confirm('Export batch and move claims to PAID?')">Export CSV</button>
                </form>
            </c:if>
        </div>
    </div>

    <table class="data-table" style="max-width:500px">
        <tr><td style="color:#6b7888">Batch Date</td><td><c:out value="${batch.batchDate}"/></td></tr>
        <tr><td style="color:#6b7888">Total Amount</td><td>$<c:out value="${batch.totalAmount}"/></td></tr>
        <tr><td style="color:#6b7888">File Reference</td><td><c:out value="${batch.fileReference}"/></td></tr>
    </table>

    <c:if test="${not empty csvContent}">
    <h2 class="section-heading">CSV Preview</h2>
    <pre style="background:#f5f7fa;padding:12px;border-radius:4px;overflow:auto;font-size:12px"><c:out value="${csvContent}"/></pre>
    </c:if>

    <p style="margin-top:16px"><a href="${pageContext.request.contextPath}/finance/batches">&larr; Back to Batches</a></p>
</div>
</body>
</html>
