<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>Member Activity — Meridian Claims</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
<style>@media print{form,.btn{display:none}}</style></head>
<body><jsp:include page="../fragments/nav.jsp"/>
<div class="content">
<h1>Member Activity Report</h1>
<form method="get" style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px">
    <input type="number" name="memberId" value="<c:out value='${memberId}'/>" placeholder="Member ID" required>
    <button type="submit" class="btn">Load</button>
    <c:if test="${not empty memberId}">
        <a href="?memberId=<c:out value='${memberId}'/>&amp;export=csv" class="btn">&#x2193; CSV</a>
    </c:if>
    <button onclick="window.print()" class="btn" type="button">&#x1F5A8; Print</button>
</form>
<c:if test="${not empty rows}">
<table class="data-table">
<thead><tr><th>Type</th><th>ID</th><th>Reference</th><th>Date</th><th>Status/Outcome</th><th>Notes</th></tr></thead>
<tbody>
<c:forEach var="r" items="${rows}">
<tr>
    <td><span class="badge badge-pending"><c:out value="${r.rec_type}"/></span></td>
    <td><c:out value="${r.rec_id}"/></td>
    <td><c:out value="${r.ref}"/></td>
    <td><c:out value="${r.event_date}"/></td>
    <td><c:out value="${r.status_or_outcome}"/></td>
    <td style="font-size:12px"><c:out value="${r.notes}"/></td>
</tr>
</c:forEach>
</tbody></table>
</c:if>
<c:if test="${empty rows and not empty memberId}"><p style="color:#9aa6b4">No activity found for member <c:out value="${memberId}"/>.</p></c:if>
<p><a href="${pageContext.request.contextPath}/reports">&larr; Reports</a></p>
</div></body></html>
