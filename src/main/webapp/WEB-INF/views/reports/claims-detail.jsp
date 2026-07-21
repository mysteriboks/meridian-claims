<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>Claims Detail — Meridian Claims</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
<style>@media print{form,.btn{display:none}}</style></head>
<body><jsp:include page="../fragments/nav.jsp"/>
<div class="content">
<h1>Claims Detail</h1>
<form method="get" style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px">
    <input type="date" name="from" value="<c:out value='${from}'/>">
    <input type="date" name="to"   value="<c:out value='${to}'/>">
    <input type="text" name="status" value="<c:out value='${statusFilter}'/>" placeholder="Status filter">
    <button type="submit" class="btn">Filter</button>
    <a href="?from=<c:out value='${from}'/>&amp;to=<c:out value='${to}'/>&amp;status=<c:out value='${statusFilter}'/>&amp;export=csv" class="btn">&#x2193; CSV</a>
    <button onclick="window.print()" class="btn" type="button">&#x1F5A8; Print</button>
</form>
<table class="data-table">
<thead><tr><th>Claim #</th><th>Type</th><th>Member</th><th>Provider</th><th>DOS</th><th>Status</th><th>Denial Code</th></tr></thead>
<tbody>
<c:forEach var="r" items="${page.items}">
<tr>
    <td><a href="${pageContext.request.contextPath}/claims/${r.id}"><c:out value="${r.claim_number}"/></a></td>
    <td><c:out value="${r.claim_type}"/></td>
    <td><c:out value="${r.first_name}"/> <c:out value="${r.last_name}"/></td>
    <td><c:out value="${r.provider_name}"/></td>
    <td><c:out value="${r.date_of_service}"/></td>
    <td><c:out value="${r.status}"/></td>
    <td><c:out value="${r.denial_reason_code}"/></td>
</tr>
</c:forEach>
<c:if test="${empty page.items}"><tr><td colspan="7" style="text-align:center;color:#9aa6b4;padding:24px">No data.</td></tr></c:if>
</tbody></table>
<c:url var="baseUrl" value="/reports/claims-detail">
  <c:param name="from"   value="${from}"/>
  <c:param name="to"     value="${to}"/>
  <c:param name="status" value="${statusFilter}"/>
</c:url>
<c:set var="baseUrl" value="${baseUrl}&amp;"/>
<jsp:include page="../fragments/pagination.jsp"/>
<p><a href="${pageContext.request.contextPath}/reports">&larr; Reports</a></p>
</div></body></html>
