<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Meridian Claims — Sign In</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body class="login-page">
<div class="login-box">
    <h1>Meridian Claims</h1>
    <h2>Sign In</h2>

    <c:if test="${not empty error}">
        <div class="alert alert-error"><c:out value="${error}"/></div>
    </c:if>
    <c:if test="${param.loggedOut != null}">
        <div class="alert alert-info">You have been signed out.</div>
    </c:if>

    <form method="post" action="${pageContext.request.contextPath}/login">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <div class="form-group">
            <label for="username">Username</label>
            <input type="text" id="username" name="username" autocomplete="username" required autofocus>
        </div>
        <div class="form-group">
            <label for="password">Password</label>
            <input type="password" id="password" name="password" autocomplete="current-password" required>
        </div>
        <button type="submit" class="btn btn-primary">Sign In</button>
    </form>
</div>
</body>
</html>
