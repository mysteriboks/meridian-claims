<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${empty member ? 'New Member' : 'Edit Member'} — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content" style="max-width:680px">
    <h1>${empty member ? 'New Member' : 'Edit Member'}</h1>

    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <c:choose>
        <c:when test="${empty member}">
            <form method="post" action="${pageContext.request.contextPath}/members/new">
        <input type="hidden" name="_csrf" value="${_csrf}">
        </c:when>
        <c:otherwise>
            <form method="post" action="${pageContext.request.contextPath}/members/${member.id}/edit">
        <input type="hidden" name="_csrf" value="${_csrf}">
        </c:otherwise>
    </c:choose>

    <c:if test="${empty member}">
        <div class="form-group">
            <label for="memberNumber">Member Number *</label>
            <input type="text" id="memberNumber" name="memberNumber" required maxlength="20">
        </div>
    </c:if>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="firstName">First Name *</label>
            <input type="text" id="firstName" name="firstName" required maxlength="100"
                   value="<c:out value='${member.firstName}'/>">
        </div>
        <div class="form-group">
            <label for="lastName">Last Name *</label>
            <input type="text" id="lastName" name="lastName" required maxlength="100"
                   value="<c:out value='${member.lastName}'/>">
        </div>
    </div>

    <div class="form-group">
        <label for="dob">Date of Birth *</label>
        <input type="date" id="dob" name="dob" required
               value="<c:if test='${not empty member.dob}'><fmt:formatDate value='${member.dob}' pattern='yyyy-MM-dd'/></c:if>">
    </div>

    <div class="form-group">
        <label for="address">Address</label>
        <input type="text" id="address" name="address" maxlength="255"
               value="<c:out value='${member.address}'/>">
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="phone">Phone</label>
            <input type="text" id="phone" name="phone" maxlength="20"
                   value="<c:out value='${member.phone}'/>">
        </div>
        <div class="form-group">
            <label for="email">Email</label>
            <input type="text" id="email" name="email" maxlength="150"
                   value="<c:out value='${member.email}'/>">
        </div>
    </div>

    <c:if test="${not empty member}">
        <div class="form-group">
            <label for="status">Status</label>
            <select id="status" name="status">
                <c:forEach var="s" items="${statuses}">
                    <option value="${s}" ${member.status == s ? 'selected' : ''}><c:out value="${s}"/></option>
                </c:forEach>
            </select>
        </div>
    </c:if>

    <div style="display:flex;gap:10px;margin-top:8px">
        <button type="submit" class="btn btn-primary">${empty member ? 'Create Member' : 'Save Changes'}</button>
        <c:choose>
            <c:when test="${not empty member}">
                <a href="${pageContext.request.contextPath}/members/${member.id}" class="btn">Cancel</a>
            </c:when>
            <c:otherwise>
                <a href="${pageContext.request.contextPath}/members" class="btn">Cancel</a>
            </c:otherwise>
        </c:choose>
    </div>
    </form>
</div>
</body>
</html>
