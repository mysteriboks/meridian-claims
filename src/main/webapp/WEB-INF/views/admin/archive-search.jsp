<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Archived Claims — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <h1>Archived Claims</h1>
    <p style="color:#6b7888;font-size:13px">Claims older than the retention threshold, moved out of active worklists. Read-only.</p>

    <form method="get" action="${pageContext.request.contextPath}/admin/operations/archive" style="display:flex;gap:8px;margin-bottom:16px">
        <input type="text" name="q" value="<c:out value='${query}'/>" placeholder="Claim number…" style="width:240px">
        <button type="submit" class="btn">Search</button>
    </form>

    <table class="data-table">
        <thead><tr><th>Claim #</th><th>Type</th><th>Member</th><th>DOS</th><th>Status</th></tr></thead>
        <tbody>
        <c:forEach var="c" items="${results}">
            <tr>
                <td><c:out value="${c.claimNumber}"/></td>
                <td><c:out value="${c.claimType}"/></td>
                <td><c:out value="${c.memberId}"/></td>
                <td><fmt:formatDate value="${c.dateOfService}" pattern="yyyy-MM-dd"/></td>
                <td><span class="badge badge-inactive"><c:out value="${c.status}"/></span></td>
            </tr>
        </c:forEach>
        <c:if test="${empty results}">
            <tr><td colspan="5" style="text-align:center;color:#9aa6b4;padding:24px">No archived claims found.</td></tr>
        </c:if>
        </tbody>
    </table>

    <p style="margin-top:16px"><a href="${pageContext.request.contextPath}/admin/operations">&larr; Back to Operations</a></p>
</div>
</body>
</html>
