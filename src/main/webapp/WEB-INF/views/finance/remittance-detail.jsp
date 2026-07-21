<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Remittance Batch #<c:out value="${batch.id}"/> — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
        <h1 style="margin:0">Remittance Batch #<c:out value="${batch.id}"/></h1>
        <div style="display:flex;gap:8px;align-items:center">
            <a href="${pageContext.request.contextPath}/finance/remittance/<c:out value="${batch.id}"/>/835" class="btn">Download 835</a>
            <c:if test="${batch.status != 'SENT'}">
                <form method="post" action="${pageContext.request.contextPath}/finance/remittance/${batch.id}/mark-sent" style="display:inline">
                    <input type="hidden" name="_csrf" value="${_csrf}">
                    <button type="submit" class="btn" onclick="return confirm('Mark batch as sent to providers?')">Mark Sent</button>
                </form>
            </c:if>
        </div>
    </div>

    <div style="border:1px solid #dde3ec;border-radius:6px;padding:16px;overflow:auto">
        ${html}
    </div>

    <p style="margin-top:16px"><a href="${pageContext.request.contextPath}/finance/remittance">&larr; Back to Remittance</a></p>
</div>
</body>
</html>
