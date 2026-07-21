<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>Claims Summary — Meridian Claims</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
<style>@media print{form,.btn{display:none}}</style></head>
<body><jsp:include page="../fragments/nav.jsp"/>
<div class="content">
<h1>Claims Summary</h1>
<form method="get" style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px">
    <input type="date" name="from" value="<c:out value='${from}'/>">
    <input type="date" name="to"   value="<c:out value='${to}'/>">
    <button type="submit" class="btn">Filter</button>
    <a href="?${from != null ? 'from='.concat(from).concat('&to=').concat(to) : ''}&export=csv" class="btn">&#x2193; CSV</a>
    <button onclick="window.print()" class="btn" type="button">&#x1F5A8; Print</button>
</form>
<table class="data-table">
<thead><tr><th>Status</th><th>Claim Count</th><th>Total Billed</th><th>Total Plan Paid</th></tr></thead>
<tbody>
<c:forEach var="r" items="${rows}">
<tr>
    <td><c:out value="${r.status}"/></td>
    <td><c:out value="${r.claim_count}"/></td>
    <td>$<fmt:formatNumber value="${r.total_billed}" pattern="#,##0.00" maxFractionDigits="2"/></td>
    <td>$<fmt:formatNumber value="${r.total_plan_paid}" pattern="#,##0.00" maxFractionDigits="2"/></td>
</tr>
</c:forEach>
<c:if test="${empty rows}"><tr><td colspan="4" style="text-align:center;color:#9aa6b4;padding:24px">No data for the selected range.</td></tr></c:if>
</tbody></table>
<p><a href="${pageContext.request.contextPath}/reports">&larr; Reports</a></p>
</div></body></html>
