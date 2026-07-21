<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Providers — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px">
        <h1 style="margin:0">Providers</h1>
        <a class="btn btn-primary" href="${pageContext.request.contextPath}/providers/new">+ New Provider</a>
    </div>

    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>

    <form method="get" action="${pageContext.request.contextPath}/providers" class="search-bar">
        <input type="text" name="q" value="<c:out value='${query}'/>" placeholder="Search name, NPI, specialty…">
        <button type="submit" class="btn">Search</button>
        <c:if test="${not empty query}"><a href="${pageContext.request.contextPath}/providers" class="btn">Clear</a></c:if>
    </form>

    <table class="data-table">
        <thead>
            <tr><th>NPI</th><th>Name</th><th>Type</th><th>Specialty</th><th>Network</th><th>Actions</th></tr>
        </thead>
        <tbody>
        <c:forEach var="p" items="${page.items}">
            <tr class="${not empty p.deletedAt ? 'row-inactive' : ''}">
                <td><c:out value="${p.npi}"/></td>
                <td><a href="${pageContext.request.contextPath}/providers/${p.id}"><c:out value="${p.name}"/></a></td>
                <td><c:out value="${p.providerType}"/></td>
                <td><c:out value="${p.specialty}"/></td>
                <td>
                    <span class="badge ${p.networkStatus == 'IN_NETWORK' ? 'badge-in-network' : 'badge-out-network'}">
                        <c:out value="${p.networkStatus}"/>
                    </span>
                </td>
                <td>
                    <a href="${pageContext.request.contextPath}/providers/${p.id}/edit" class="btn-link">Edit</a>
                    <c:if test="${empty p.deletedAt}">
                        <form method="post" action="${pageContext.request.contextPath}/providers/${p.id}/deactivate" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                            <button type="submit" class="btn-link btn-danger" onclick="return confirm('Deactivate provider?')">Deactivate</button>
                        </form>
                    </c:if>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty page.items}">
            <tr><td colspan="6" style="color:#9aa6b4;text-align:center;padding:24px">No providers found.</td></tr>
        </c:if>
        </tbody>
    </table>

    <c:set var="baseUrl" value="${pageContext.request.contextPath}/providers?q=${query}&"/>
    <jsp:include page="../fragments/pagination.jsp"/>
</div>
</body>
</html>
