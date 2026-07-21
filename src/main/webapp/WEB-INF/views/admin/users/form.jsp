<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Meridian Claims — New User</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../../fragments/nav.jsp"/>
<div class="content">
    <h1>New User</h1>

    <c:if test="${not empty error}">
        <div class="alert alert-error"><c:out value="${error}"/></div>
    </c:if>

    <form method="post" action="${pageContext.request.contextPath}/admin/users/new">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <div class="form-group">
            <label for="username">Username</label>
            <input type="text" id="username" name="username" required maxlength="100">
        </div>
        <div class="form-group">
            <label for="fullName">Full Name</label>
            <input type="text" id="fullName" name="fullName" required maxlength="200">
        </div>
        <div class="form-group">
            <label for="role">Role</label>
            <select id="role" name="role" required>
                <c:forEach var="r" items="${roles}">
                    <option value="${r}"><c:out value="${r}"/></option>
                </c:forEach>
            </select>
        </div>
        <div class="form-group">
            <label for="tempPassword">Temporary Password</label>
            <input type="password" id="tempPassword" name="tempPassword" required>
            <small>User will be required to change on first login. Min 8 chars, at least one number and one special character.</small>
        </div>
        <button type="submit" class="btn btn-primary">Create User</button>
        <a href="${pageContext.request.contextPath}/admin/users" class="btn">Cancel</a>
    </form>
</div>
</body>
</html>
