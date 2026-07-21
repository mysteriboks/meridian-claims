<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Meridian Claims — Users</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../../fragments/nav.jsp"/>
<div class="content">
    <h1>User Management</h1>

    <c:if test="${not empty success}">
        <div class="alert alert-success"><c:out value="${success}"/></div>
    </c:if>

    <a href="${pageContext.request.contextPath}/admin/users/new" class="btn btn-primary">New User</a>

    <table class="data-table">
        <thead>
        <tr>
            <th>Username</th>
            <th>Full Name</th>
            <th>Role</th>
            <th>Status</th>
            <th>Last Login</th>
            <th>Actions</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="u" items="${users}">
            <tr class="${u.active ? '' : 'row-inactive'}">
                <td><c:out value="${u.username}"/></td>
                <td><c:out value="${u.fullName}"/></td>
                <td><c:out value="${u.role}"/></td>
                <td>
                    <c:choose>
                        <c:when test="${!u.active}">Inactive</c:when>
                        <c:when test="${u.locked}">Locked</c:when>
                        <c:when test="${u.forceReset}">Reset Required</c:when>
                        <c:otherwise>Active</c:otherwise>
                    </c:choose>
                </td>
                <td>
                    <c:choose>
                        <c:when test="${not empty u.lastLoginAt}">
                            <fmt:formatDate value="${u.lastLoginAt}" pattern="yyyy-MM-dd HH:mm"/>
                        </c:when>
                        <c:otherwise>Never</c:otherwise>
                    </c:choose>
                </td>
                <td>
                    <a href="${pageContext.request.contextPath}/admin/users/${u.id}/edit">Edit</a>
                    <c:if test="${u.active}">
                        <form method="post" action="${pageContext.request.contextPath}/admin/users/${u.id}/deactivate" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                            <button type="submit" class="btn-link btn-danger" onclick="return confirm('Deactivate this user?')">Deactivate</button>
                        </form>
                    </c:if>
                    <c:if test="${!u.active}">
                        <form method="post" action="${pageContext.request.contextPath}/admin/users/${u.id}/activate" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                            <button type="submit" class="btn-link">Activate</button>
                        </form>
                    </c:if>
                    <c:if test="${u.locked}">
                        <form method="post" action="${pageContext.request.contextPath}/admin/users/${u.id}/unlock" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                            <button type="submit" class="btn-link">Unlock</button>
                        </form>
                    </c:if>
                    <form method="post" action="${pageContext.request.contextPath}/admin/users/${u.id}/force-reset" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                        <button type="submit" class="btn-link">Force Reset</button>
                    </form>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>

    <h2>Role Permissions</h2>
    <table class="data-table">
        <thead>
        <tr>
            <th>Role</th>
            <th>Permitted Areas</th>
        </tr>
        </thead>
        <tbody>
        <tr><td>ADMIN</td><td>All screens including user management, admin tools, all claim operations</td></tr>
        <tr><td>REVIEWER</td><td>Claim review queue, approve/deny/pending-info, SLA tracking</td></tr>
        <tr><td>STAFF</td><td>Claim submission, member/provider lookup, info requests, own claims</td></tr>
        <tr><td>FINANCE</td><td>Payment processing, payment batch export, remittance advice, EOB mailing</td></tr>
        <tr><td>ANALYST</td><td>Reports and dashboard (read-only)</td></tr>
        </tbody>
    </table>
</div>
</body>
</html>
