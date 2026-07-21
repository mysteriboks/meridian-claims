<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>EOB — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
        <h1 style="margin:0">EOB — Claim <c:out value="${eob.claimId}"/></h1>
        <div style="display:flex;gap:8px">
            <c:if test="${eob.deliveryMethod == 'PENDING'}">
                <form method="post" action="${pageContext.request.contextPath}/finance/eobs/${eob.id}/mark-mailed" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                    <button type="submit" class="btn" onclick="return confirm('Mark as mailed?')">Mark Mailed</button>
                </form>
            </c:if>
        </div>
    </div>

    <div style="border:1px solid #dde3ec;border-radius:6px;padding:16px">
        ${eob.content}
    </div>

    <p style="margin-top:16px">
        Delivery: <strong><c:out value="${eob.deliveryMethod}"/></strong>
        <c:if test="${not empty eob.deliveredAt}">
            &nbsp;on <fmt:formatDate value="${eob.deliveredAt}" pattern="yyyy-MM-dd HH:mm"/>
        </c:if>
    </p>
    <p><a href="${pageContext.request.contextPath}/finance/eobs">&larr; Back to EOBs</a></p>
</div>
</body>
</html>
