<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>Payment Report — Meridian Claims</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
<style>@media print{form,.btn{display:none}}</style></head>
<body><jsp:include page="../fragments/nav.jsp"/>
<div class="content">
<h1>Payment Report</h1>
<form method="get" style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px">
    <input type="date" name="from" value="<c:out value='${from}'/>">
    <input type="date" name="to"   value="<c:out value='${to}'/>">
    <button type="submit" class="btn">Filter</button>
    <a href="?from=<c:out value='${from}'/>&amp;to=<c:out value='${to}'/>&amp;export=csv" class="btn">&#x2193; CSV</a>
    <button onclick="window.print()" class="btn" type="button">&#x1F5A8; Print</button>
</form>
<table class="data-table">
<thead><tr><th>Payment</th><th>Claim</th><th>Provider</th><th>NPI</th><th>Plan Paid</th><th>Status</th><th>Date</th><th>Batch</th></tr></thead>
<tbody>
<c:forEach var="r" items="${rows}">
<tr>
    <td><c:out value="${r.payment_id}"/></td>
    <td><a href="${pageContext.request.contextPath}/claims/${r.payment_id}"><c:out value="${r.claim_number}"/></a></td>
    <td><c:out value="${r.provider_name}"/></td>
    <td><c:out value="${r.npi}"/></td>
    <td>$<c:out value="${r.plan_paid_total}"/></td>
    <td><c:out value="${r.status}"/></td>
    <td><c:out value="${r.payment_date}"/></td>
    <td><c:out value="${r.batch_id}"/></td>
</tr>
</c:forEach>
<c:if test="${empty rows}"><tr><td colspan="8" style="text-align:center;color:#9aa6b4;padding:24px">No payments in range.</td></tr></c:if>
</tbody></table>
<p><a href="${pageContext.request.contextPath}/reports">&larr; Reports</a></p>
</div></body></html>
