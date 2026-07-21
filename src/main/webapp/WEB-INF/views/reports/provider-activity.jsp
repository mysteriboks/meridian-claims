<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>Provider Activity — Meridian Claims</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
<style>@media print{form,.btn{display:none}}</style></head>
<body><jsp:include page="../fragments/nav.jsp"/>
<div class="content">
<h1>Provider Activity Report</h1>
<form method="get" style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px">
    <input type="number" name="providerId" value="<c:out value='${providerId}'/>" placeholder="Provider ID" required>
    <button type="submit" class="btn">Load</button>
    <c:if test="${not empty providerId}"><a href="?providerId=${providerId}&export=csv" class="btn">&#x2193; CSV</a></c:if>
    <button onclick="window.print()" class="btn" type="button">&#x1F5A8; Print</button>
</form>
<c:if test="${not empty rows}">
<table class="data-table">
<thead><tr><th>Claim #</th><th>DOS</th><th>Status</th><th>Billed</th><th>Plan Paid</th><th>Payment Date</th><th>Remit Batch</th></tr></thead>
<tbody>
<c:forEach var="r" items="${rows}">
<tr>
    <td><c:out value="${r.claim_number}"/></td>
    <td><c:out value="${r.date_of_service}"/></td>
    <td><c:out value="${r.status}"/></td>
    <td>$<c:out value="${r.billed}"/></td>
    <td>$<c:out value="${r.plan_paid}"/></td>
    <td><c:out value="${r.payment_date}"/></td>
    <td><c:out value="${r.remit_batch_id}"/></td>
</tr>
</c:forEach>
</tbody></table>
</c:if>
<p><a href="${pageContext.request.contextPath}/reports">&larr; Reports</a></p>
</div></body></html>
