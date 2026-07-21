<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Referral <c:out value="${referral.referralNumber}"/> — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>

    <div style="display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:16px">
        <div>
            <h1 style="margin:0 0 4px">Referral: <c:out value="${referral.referralNumber}"/></h1>
            <span class="badge badge-${referral.status == 'ACTIVE' ? 'active' : 'expired'}"><c:out value="${referral.status}"/></span>
        </div>
        <div style="display:flex;gap:8px">
            <a class="btn" href="${pageContext.request.contextPath}/referrals/${referral.id}/edit">Edit</a>
            <c:if test="${referral.status == 'ACTIVE'}">
                <form method="post" action="${pageContext.request.contextPath}/referrals/${referral.id}/expire" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                    <button type="submit" class="btn btn-danger" onclick="return confirm('Expire this referral?')">Expire</button>
                </form>
            </c:if>
        </div>
    </div>

    <table class="data-table" style="max-width:600px">
        <tr><td style="color:#6b7888;width:200px">Member</td><td><c:out value="${referral.memberName}"/></td></tr>
        <tr><td style="color:#6b7888">Referring Provider</td><td><c:out value="${referral.referringProviderName}"/></td></tr>
        <tr><td style="color:#6b7888">Referred-To Provider</td><td><c:out value="${referral.referredToProviderName}"/></td></tr>
        <tr><td style="color:#6b7888">Service Type</td><td><c:out value="${referral.serviceType}"/></td></tr>
        <tr><td style="color:#6b7888">Valid From</td><td><fmt:formatDate value="${referral.validFrom}" pattern="MMM d, yyyy"/></td></tr>
        <tr><td style="color:#6b7888">Valid To</td><td><fmt:formatDate value="${referral.validTo}" pattern="MMM d, yyyy"/></td></tr>
        <tr><td style="color:#6b7888">Notes</td><td><c:out value="${referral.notes}"/></td></tr>
    </table>

    <p style="margin-top:20px"><a href="${pageContext.request.contextPath}/referrals">&larr; Back to Referrals</a></p>
</div>
</body>
</html>
