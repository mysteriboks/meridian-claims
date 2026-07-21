<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Subrogation — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <h1>Open Subrogation Cases</h1>
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <table class="data-table">
        <thead><tr><th>ID</th><th>Claim</th><th>Opened</th><th>Status</th><th>Liable Party</th><th>Recovery</th><th>Actions</th></tr></thead>
        <tbody>
        <c:forEach var="sc" items="${cases}">
            <tr>
                <td><c:out value="${sc.id}"/></td>
                <td><a href="${pageContext.request.contextPath}/claims/${sc.claimId}"><c:out value="${sc.claimId}"/></a></td>
                <td><c:out value="${sc.openedDate}"/></td>
                <td><span class="badge badge-pending"><c:out value="${sc.status}"/></span></td>
                <td><c:out value="${sc.liableParty}"/></td>
                <td><c:if test="${not empty sc.recoveryAmount}">$<c:out value="${sc.recoveryAmount}"/></c:if></td>
                <td><a href="${pageContext.request.contextPath}/finance/subrogation/${sc.id}" class="btn-link">Manage</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty cases}">
            <tr><td colspan="7" style="text-align:center;color:#9aa6b4;padding:24px">No open cases.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>
</body>
</html>
