<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title><c:out value="${provider.name}"/> — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>

    <div style="display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:16px">
        <div>
            <h1 style="margin:0 0 4px"><c:out value="${provider.name}"/></h1>
            <span style="color:#6b7888;font-size:13px">NPI: <c:out value="${provider.npi}"/></span>
            &nbsp;<span class="badge ${provider.networkStatus == 'IN_NETWORK' ? 'badge-in-network' : 'badge-out-network'}">
                <c:out value="${provider.networkStatus}"/>
            </span>
        </div>
        <div style="display:flex;gap:8px">
            <a class="btn" href="${pageContext.request.contextPath}/providers/${provider.id}/edit">Edit</a>
            <c:if test="${empty provider.deletedAt}">
                <form method="post" action="${pageContext.request.contextPath}/providers/${provider.id}/deactivate" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                    <button type="submit" class="btn btn-danger" onclick="return confirm('Deactivate provider?')">Deactivate</button>
                </form>
            </c:if>
        </div>
    </div>

    <table class="data-table" style="max-width:600px">
        <tr><td style="color:#6b7888;width:160px">Type</td><td><c:out value="${provider.providerType}"/></td></tr>
        <tr><td style="color:#6b7888">Specialty</td><td><c:out value="${provider.specialty}"/></td></tr>
        <tr><td style="color:#6b7888">Phone</td><td><c:out value="${provider.phone}"/></td></tr>
        <tr><td style="color:#6b7888">Address</td><td><c:out value="${provider.address}"/></td></tr>
    </table>

    <p style="margin-top:20px"><a href="${pageContext.request.contextPath}/providers">&larr; Back to Providers</a></p>
</div>
</body>
</html>
