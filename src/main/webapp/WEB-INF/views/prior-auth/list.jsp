<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Prior Authorizations — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px">
        <div>
            <h1 style="margin:0">Prior Authorizations</h1>
            <c:if test="${not empty member}">
                <span style="color:#6b7888;font-size:13px">For: <c:out value="${member.fullName}"/></span>
            </c:if>
        </div>
        <a class="btn btn-primary"
           href="${pageContext.request.contextPath}/prior-auth/new${not empty member ? '?memberId='.concat(member.id) : ''}">+ New Authorization</a>
    </div>
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>

    <table class="data-table">
        <thead>
            <tr><th>Auth #</th><th>Member</th><th>Provider</th><th>Procedure</th><th>Valid From</th><th>Valid To</th><th>Status</th><th>Actions</th></tr>
        </thead>
        <tbody>
        <c:forEach var="a" items="${page.items}">
            <tr>
                <td><a href="${pageContext.request.contextPath}/prior-auth/${a.id}"><c:out value="${a.authNumber}"/></a></td>
                <td><c:out value="${a.memberName}"/></td>
                <td><c:out value="${a.providerName}"/></td>
                <td><c:out value="${a.procedureCode}"/></td>
                <td><fmt:formatDate value="${a.authorizedFrom}" pattern="yyyy-MM-dd"/></td>
                <td><fmt:formatDate value="${a.authorizedTo}" pattern="yyyy-MM-dd"/></td>
                <td><span class="badge badge-${a.status == 'ACTIVE' ? 'active' : 'expired'}"><c:out value="${a.status}"/></span></td>
                <td><a href="${pageContext.request.contextPath}/prior-auth/${a.id}" class="btn-link">View</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty page.items}">
            <tr><td colspan="8" style="color:#9aa6b4;text-align:center;padding:24px">No prior authorizations found.</td></tr>
        </c:if>
        </tbody>
    </table>

    <c:set var="baseUrl" value="${pageContext.request.contextPath}/prior-auth?${not empty member ? 'memberId='.concat(member.id).concat('&') : ''}"/>
    <jsp:include page="../fragments/pagination.jsp"/>
</div>
</body>
</html>
