<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Members — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">
        <h1 style="margin:0">Members</h1>
        <a class="btn btn-primary" href="${pageContext.request.contextPath}/members/new">+ New Member</a>
    </div>

    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <form method="get" action="${pageContext.request.contextPath}/members" class="search-bar">
        <input type="text" name="q" value="<c:out value='${query}'/>" placeholder="Search name, member #…">
        <button type="submit" class="btn">Search</button>
        <c:if test="${not empty query}">
            <a href="${pageContext.request.contextPath}/members" class="btn">Clear</a>
        </c:if>
    </form>

    <table class="data-table">
        <thead>
            <tr>
                <th>Member #</th><th>Name</th><th>DOB</th><th>Status</th><th>Actions</th>
            </tr>
        </thead>
        <tbody>
        <c:forEach var="m" items="${page.items}">
            <tr class="${m.status != 'ACTIVE' ? 'row-inactive' : ''}">
                <td><c:out value="${m.memberNumber}"/></td>
                <td><a href="${pageContext.request.contextPath}/members/${m.id}"><c:out value="${m.fullName}"/></a></td>
                <td><c:out value="${m.dob}"/></td>
                <td><span class="badge badge-${m.status == 'ACTIVE' ? 'active' : 'inactive'}"><c:out value="${m.status}"/></span></td>
                <td>
                    <a href="${pageContext.request.contextPath}/members/${m.id}/edit" class="btn-link">Edit</a>
                    <c:if test="${m.status == 'ACTIVE'}">
                        <form method="post" action="${pageContext.request.contextPath}/members/${m.id}/deactivate" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                            <button type="submit" class="btn-link btn-danger" onclick="return confirm('Deactivate this member?')">Deactivate</button>
                        </form>
                    </c:if>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty page.items}">
            <tr><td colspan="5" style="color:#9aa6b4;text-align:center;padding:24px">No members found.</td></tr>
        </c:if>
        </tbody>
    </table>

    <c:set var="baseUrl" value="${pageContext.request.contextPath}/members?q=${query}&"/>
    <jsp:include page="../fragments/pagination.jsp"/>
</div>
</body>
</html>
