<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Prior Auth <c:out value="${auth.authNumber}"/> — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>

    <div style="display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:16px">
        <div>
            <h1 style="margin:0 0 4px">Prior Auth: <c:out value="${auth.authNumber}"/></h1>
            <span class="badge badge-${auth.status == 'ACTIVE' ? 'active' : 'expired'}"><c:out value="${auth.status}"/></span>
        </div>
        <div style="display:flex;gap:8px">
            <a class="btn" href="${pageContext.request.contextPath}/prior-auth/${auth.id}/edit">Edit</a>
            <c:if test="${auth.status == 'ACTIVE'}">
                <form method="post" action="${pageContext.request.contextPath}/prior-auth/${auth.id}/expire" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                    <button type="submit" class="btn btn-danger" onclick="return confirm('Expire this authorization?')">Expire</button>
                </form>
            </c:if>
        </div>
    </div>

    <table class="data-table" style="max-width:600px">
        <tr><td style="color:#6b7888;width:180px">Member</td><td><c:out value="${auth.memberName}"/></td></tr>
        <tr><td style="color:#6b7888">Provider</td><td><c:out value="${auth.providerName}"/></td></tr>
        <tr><td style="color:#6b7888">Procedure Code</td><td><c:out value="${auth.procedureCode}"/></td></tr>
        <tr><td style="color:#6b7888">Service Type</td><td><c:out value="${auth.serviceType}"/></td></tr>
        <tr><td style="color:#6b7888">Authorized From</td><td><fmt:formatDate value="${auth.authorizedFrom}" pattern="MMM d, yyyy"/></td></tr>
        <tr><td style="color:#6b7888">Authorized To</td><td><fmt:formatDate value="${auth.authorizedTo}" pattern="MMM d, yyyy"/></td></tr>
        <tr><td style="color:#6b7888">Approved Units</td><td><c:out value="${auth.approvedUnits}"/></td></tr>
        <tr><td style="color:#6b7888">Notes</td><td><c:out value="${auth.notes}"/></td></tr>
    </table>

    <p style="margin-top:20px"><a href="${pageContext.request.contextPath}/prior-auth">&larr; Back to Prior Auths</a></p>
</div>
</body>
</html>
