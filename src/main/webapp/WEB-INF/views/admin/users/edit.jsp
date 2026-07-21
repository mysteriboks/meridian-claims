<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Meridian Claims — Edit User</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../../fragments/nav.jsp"/>
<div class="content">
    <h1>Edit User — <c:out value="${user.username}"/></h1>

    <c:if test="${not empty error}">
        <div class="alert alert-error"><c:out value="${error}"/></div>
    </c:if>

    <form method="post" action="${pageContext.request.contextPath}/admin/users/${user.id}/edit">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <div class="form-group">
            <label>Username</label>
            <span class="field-value"><c:out value="${user.username}"/></span>
        </div>
        <div class="form-group">
            <label for="fullName">Full Name</label>
            <input type="text" id="fullName" name="fullName" value="<c:out value='${user.fullName}'/>" required maxlength="200">
        </div>
        <div class="form-group">
            <label for="role">Role</label>
            <select id="role" name="role" required>
                <c:forEach var="r" items="${roles}">
                    <option value="${r}" ${user.role == r ? 'selected' : ''}><c:out value="${r}"/></option>
                </c:forEach>
            </select>
        </div>
        <button type="submit" class="btn btn-primary">Save</button>
        <a href="${pageContext.request.contextPath}/admin/users" class="btn">Cancel</a>
    </form>
</div>
</body>
</html>
