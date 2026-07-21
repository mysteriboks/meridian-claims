<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>EOB Documents — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <h1>Explanation of Benefits</h1>
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <form method="get" action="${pageContext.request.contextPath}/finance/eobs" style="display:flex;gap:8px;margin-bottom:16px">
        <input type="number" name="memberId" value="<c:out value='${memberId}'/>" placeholder="Member ID" style="width:160px">
        <button type="submit" class="btn">Search</button>
        <c:if test="${not empty memberId}">
            <a href="${pageContext.request.contextPath}/finance/eobs" class="btn">Clear</a>
        </c:if>
    </form>

    <c:if test="${not empty eobs}">
    <table class="data-table">
        <thead>
            <tr><th>ID</th><th>Claim</th><th>Member</th><th>Generated</th><th>Delivery</th><th>Actions</th></tr>
        </thead>
        <tbody>
        <c:forEach var="e" items="${eobs}">
            <tr>
                <td><c:out value="${e.id}"/></td>
                <td><a href="${pageContext.request.contextPath}/claims/${e.claimId}"><c:out value="${e.claimId}"/></a></td>
                <td><c:out value="${e.memberId}"/></td>
                <td><fmt:formatDate value="${e.generatedAt}" pattern="yyyy-MM-dd HH:mm"/></td>
                <td><span class="badge badge-${e.deliveryMethod == 'PENDING' ? 'pending' : 'active'}"><c:out value="${e.deliveryMethod}"/></span></td>
                <td><a href="${pageContext.request.contextPath}/finance/eobs/${e.id}" class="btn-link">View</a></td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
    </c:if>
    <c:if test="${empty eobs and not empty memberId}">
        <p style="color:#9aa6b4">No EOBs found for member <c:out value="${memberId}"/>.</p>
    </c:if>
</div>
</body>
</html>
