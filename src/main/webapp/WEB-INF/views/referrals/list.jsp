<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Referrals — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px">
        <div>
            <h1 style="margin:0">Referrals</h1>
            <c:if test="${not empty member}">
                <span style="color:#6b7888;font-size:13px">For: <c:out value="${member.fullName}"/></span>
            </c:if>
        </div>
        <a class="btn btn-primary"
           href="${pageContext.request.contextPath}/referrals/new${not empty member ? '?memberId='.concat(member.id) : ''}">+ New Referral</a>
    </div>
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>

    <table class="data-table">
        <thead>
            <tr><th>Referral #</th><th>Member</th><th>Referring Provider</th><th>Referred To</th><th>Service Type</th><th>Valid From</th><th>Valid To</th><th>Status</th></tr>
        </thead>
        <tbody>
        <c:forEach var="r" items="${page.items}">
            <tr>
                <td><a href="${pageContext.request.contextPath}/referrals/${r.id}"><c:out value="${r.referralNumber}"/></a></td>
                <td><c:out value="${r.memberName}"/></td>
                <td><c:out value="${r.referringProviderName}"/></td>
                <td><c:out value="${r.referredToProviderName}"/></td>
                <td><c:out value="${r.serviceType}"/></td>
                <td><fmt:formatDate value="${r.validFrom}" pattern="yyyy-MM-dd"/></td>
                <td><fmt:formatDate value="${r.validTo}" pattern="yyyy-MM-dd"/></td>
                <td><span class="badge badge-${r.status == 'ACTIVE' ? 'active' : 'expired'}"><c:out value="${r.status}"/></span></td>
            </tr>
        </c:forEach>
        <c:if test="${empty page.items}">
            <tr><td colspan="8" style="color:#9aa6b4;text-align:center;padding:24px">No referrals found.</td></tr>
        </c:if>
        </tbody>
    </table>

    <c:set var="baseUrl" value="${pageContext.request.contextPath}/referrals?${not empty member ? 'memberId='.concat(member.id).concat('&') : ''}"/>
    <jsp:include page="../fragments/pagination.jsp"/>
</div>
</body>
</html>
