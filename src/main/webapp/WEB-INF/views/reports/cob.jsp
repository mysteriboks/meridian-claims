<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>COB Report — Meridian Claims</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
<style>@media print{form,.btn{display:none}}</style></head>
<body><jsp:include page="../fragments/nav.jsp"/>
<div class="content">
<h1>COB Report</h1>
<form method="get" style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px">
    <input type="date" name="from" value="<c:out value='${from}'/>">
    <input type="date" name="to"   value="<c:out value='${to}'/>">
    <button type="submit" class="btn">Filter</button>
    <a href="?from=<c:out value='${from}'/>&amp;to=<c:out value='${to}'/>&amp;export=csv" class="btn">&#x2193; CSV</a>
    <button onclick="window.print()" class="btn" type="button">&#x1F5A8; Print</button>
</form>
<table class="data-table">
<thead><tr><th>Claim #</th><th>DOS</th><th>Coverage</th><th>Primary Paid</th><th>Secondary Paid</th><th>Member Resp.</th><th>Total Allowed</th></tr></thead>
<tbody>
<c:forEach var="r" items="${rows}">
<tr>
    <td><c:out value="${r.claim_number}"/></td>
    <td><c:out value="${r.date_of_service}"/></td>
    <td><c:out value="${r.coverage_order}"/></td>
    <td>$<c:out value="${r.cob_primary_paid}"/></td>
    <td>$<c:out value="${r.secondary_plan_paid}"/></td>
    <td>$<c:out value="${r.member_resp}"/></td>
    <td>$<c:out value="${r.total_allowed}"/></td>
</tr>
</c:forEach>
<c:if test="${empty rows}"><tr><td colspan="7" style="text-align:center;color:#9aa6b4;padding:24px">No secondary claims in range.</td></tr></c:if>
</tbody></table>
<p><a href="${pageContext.request.contextPath}/reports">&larr; Reports</a></p>
</div></body></html>
