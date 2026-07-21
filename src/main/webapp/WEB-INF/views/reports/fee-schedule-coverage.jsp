<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>Fee Schedule Coverage — Meridian Claims</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
<style>@media print{.btn{display:none}}</style></head>
<body><jsp:include page="../fragments/nav.jsp"/>
<div class="content">
<h1>Fee Schedule Coverage Report</h1>
<p style="color:#6b7888;font-size:13px">Procedure codes billed with no fee schedule rate on file (NO_RATE). Add rates in Admin → Plans → Fee Schedule to resolve.</p>
<div style="display:flex;gap:8px;margin-bottom:16px">
    <a href="?export=csv" class="btn">&#x2193; CSV</a>
    <button onclick="window.print()" class="btn" type="button">&#x1F5A8; Print</button>
</div>
<table class="data-table">
<thead><tr><th>Procedure Code</th><th>NO_RATE Count</th><th>Latest DOS</th></tr></thead>
<tbody>
<c:forEach var="r" items="${rows}">
<tr>
    <td><c:out value="${r.procedure_code}"/></td>
    <td><c:out value="${r.no_rate_count}"/></td>
    <td><c:out value="${r.latest_dos}"/></td>
</tr>
</c:forEach>
<c:if test="${empty rows}"><tr><td colspan="3" style="text-align:center;color:#9aa6b4;padding:24px">No NO_RATE line items found — fee schedule coverage is complete.</td></tr></c:if>
</tbody></table>
<p><a href="${pageContext.request.contextPath}/reports">&larr; Reports</a></p>
</div></body></html>
