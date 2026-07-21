<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>Subrogation Report — Meridian Claims</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
<style>@media print{form,.btn{display:none}}</style></head>
<body><jsp:include page="../fragments/nav.jsp"/>
<div class="content">
<h1>Subrogation Report</h1>
<div style="display:flex;gap:8px;margin-bottom:16px">
    <a href="?export=csv" class="btn">&#x2193; CSV</a>
    <button onclick="window.print()" class="btn" type="button">&#x1F5A8; Print</button>
</div>
<table class="data-table">
<thead><tr><th>Case</th><th>Claim</th><th>DOS</th><th>Opened</th><th>Status</th><th>Liable Party</th><th>Recovery</th><th>Plan Paid</th></tr></thead>
<tbody>
<c:forEach var="r" items="${rows}">
<tr>
    <td><c:out value="${r.id}"/></td>
    <td><c:out value="${r.claim_number}"/></td>
    <td><c:out value="${r.date_of_service}"/></td>
    <td><c:out value="${r.opened_date}"/></td>
    <td><c:out value="${r.status}"/></td>
    <td><c:out value="${r.liable_party}"/></td>
    <td><c:if test="${not empty r.recovery_amount}">$<c:out value="${r.recovery_amount}"/></c:if></td>
    <td>$<c:out value="${r.plan_paid_on_claim}"/></td>
</tr>
</c:forEach>
<c:if test="${empty rows}"><tr><td colspan="8" style="text-align:center;color:#9aa6b4;padding:24px">No subrogation cases.</td></tr></c:if>
</tbody></table>
<p><a href="${pageContext.request.contextPath}/reports">&larr; Reports</a></p>
</div></body></html>
