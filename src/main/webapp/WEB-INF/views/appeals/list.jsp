<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Appeals — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <h1>Open Appeals</h1>
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <table class="data-table">
        <thead>
            <tr><th>ID</th><th>Claim</th><th>Type</th><th>Submitted</th><th>Deadline</th><th>Status</th><th>Actions</th></tr>
        </thead>
        <tbody>
        <c:forEach var="a" items="${appeals}">
            <c:set var="breached" value="${a.status == 'OPEN' and a.deadlineDate lt today}"/>
            <tr<c:if test="${breached}"> style="background:#fff3cd"</c:if>>
                <td><c:out value="${a.id}"/></td>
                <td><a href="${pageContext.request.contextPath}/claims/${a.claimId}"><c:out value="${a.claimId}"/></a></td>
                <td><c:out value="${a.appealType}"/></td>
                <td><fmt:formatDate value="${a.submittedDate}" pattern="yyyy-MM-dd"/></td>
                <td>
                    <fmt:formatDate value="${a.deadlineDate}" pattern="yyyy-MM-dd"/>
                    <c:if test="${breached}"><span class="badge badge-inactive">SLA BREACH</span></c:if>
                </td>
                <td><span class="badge badge-${a.status == 'OPEN' ? 'pending' : a.status == 'APPROVED' ? 'active' : 'inactive'}"><c:out value="${a.status}"/></span></td>
                <td><a href="${pageContext.request.contextPath}/appeals/${a.id}" class="btn-link">View</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty appeals}">
            <tr><td colspan="7" style="text-align:center;color:#9aa6b4;padding:24px">No open appeals.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>
</body>
</html>
