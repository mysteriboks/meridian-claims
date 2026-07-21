<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Audit Log — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <h1>Audit Log</h1>

    <form method="get" action="${pageContext.request.contextPath}/admin/audit" style="margin-bottom:16px;display:flex;gap:8px;flex-wrap:wrap;align-items:flex-end">
        <div>
            <label>Username<br>
                <input type="text" name="username" value="<c:out value="${username}"/>" placeholder="Any" style="width:140px">
            </label>
        </div>
        <div>
            <label>Event type<br>
                <input type="text" name="eventType" value="<c:out value="${eventType}"/>" placeholder="e.g. CREATE" style="width:120px">
            </label>
        </div>
        <div>
            <label>Entity type<br>
                <input type="text" name="entityType" value="<c:out value="${entityType}"/>" placeholder="e.g. MEMBER" style="width:120px">
            </label>
        </div>
        <div>
            <label>From<br>
                <input type="date" name="from" value="<c:if test="${not empty from}"><fmt:formatDate value="${from}" pattern="yyyy-MM-dd"/></c:if>">
            </label>
        </div>
        <div>
            <label>To<br>
                <input type="date" name="to" value="<c:if test="${not empty to}"><fmt:formatDate value="${to}" pattern="yyyy-MM-dd"/></c:if>">
            </label>
        </div>
        <div>
            <button type="submit">Search</button>
            <a href="${pageContext.request.contextPath}/admin/audit" style="margin-left:8px">Clear</a>
        </div>
    </form>

    <p><strong><c:out value="${auditPage.totalItems}"/></strong> entries found
       (page <c:out value="${auditPage.pageNumber}"/> of <c:out value="${auditPage.totalPages}"/>)</p>

    <table>
        <thead>
            <tr>
                <th>Date/Time</th>
                <th>User</th>
                <th>Event</th>
                <th>Entity</th>
                <th>Entity ID</th>
                <th>Description</th>
            </tr>
        </thead>
        <tbody>
        <c:forEach var="entry" items="${auditPage.items}">
            <tr>
                <td><fmt:formatDate value="${entry.createdAt}" pattern="yyyy-MM-dd HH:mm:ss"/></td>
                <td><c:out value="${not empty entry.username ? entry.username : entry.userId}"/></td>
                <td><c:out value="${entry.eventType}"/></td>
                <td><c:out value="${entry.entityType}"/></td>
                <td><c:out value="${entry.entityId}"/></td>
                <td><c:out value="${entry.description}"/></td>
            </tr>
        </c:forEach>
        <c:if test="${empty auditPage.items}">
            <tr><td colspan="6" style="text-align:center">No entries match the selected filters.</td></tr>
        </c:if>
        </tbody>
    </table>

    <c:if test="${auditPage.totalPages > 1}">
        <div style="margin-top:12px;display:flex;gap:6px">
            <c:if test="${auditPage.pageNumber > 1}">
                <a href="${pageContext.request.contextPath}/admin/audit?page=${auditPage.pageNumber - 1}&username=<c:out value="${username}"/>&eventType=<c:out value="${eventType}"/>&entityType=<c:out value="${entityType}"/>">&#8592; Prev</a>
            </c:if>
            <span>Page <c:out value="${auditPage.pageNumber}"/> / <c:out value="${auditPage.totalPages}"/></span>
            <c:if test="${auditPage.pageNumber < auditPage.totalPages}">
                <a href="${pageContext.request.contextPath}/admin/audit?page=${auditPage.pageNumber + 1}&username=<c:out value="${username}"/>&eventType=<c:out value="${eventType}"/>&entityType=<c:out value="${entityType}"/>">Next &#8594;</a>
            </c:if>
        </div>
    </c:if>
</div>
</body>
</html>
