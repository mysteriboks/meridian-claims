<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Fee Schedule — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../../fragments/nav.jsp"/>
<div class="content">
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>

    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px">
        <div>
            <h1 style="margin:0 0 4px">Fee Schedule</h1>
            <span style="color:#6b7888;font-size:13px">Plan: <c:out value="${plan.planName}"/></span>
        </div>
        <a class="btn btn-primary"
           href="${pageContext.request.contextPath}/admin/fee-schedule/new?planId=${plan.id}">+ Add Rate</a>
    </div>

    <table class="data-table">
        <thead>
            <tr><th>Procedure</th><th>Provider</th><th>Allowed Amount</th><th>Effective</th><th>Terminates</th><th>Actions</th></tr>
        </thead>
        <tbody>
        <c:forEach var="r" items="${page.items}">
            <tr>
                <td><c:out value="${r.procedureCode}"/></td>
                <td><c:out value="${not empty r.providerName ? r.providerName : '(Plan-wide)'}"/></td>
                <td>$<c:out value="${r.allowedAmount}"/></td>
                <td><fmt:formatDate value="${r.effectiveDate}" pattern="yyyy-MM-dd"/></td>
                <td>
                    <c:choose>
                        <c:when test="${not empty r.terminationDate}"><fmt:formatDate value="${r.terminationDate}" pattern="yyyy-MM-dd"/></c:when>
                        <c:otherwise><span style="color:#9aa6b4">—</span></c:otherwise>
                    </c:choose>
                </td>
                <td>
                    <a href="${pageContext.request.contextPath}/admin/fee-schedule/${r.id}/edit" class="btn-link">Edit</a>
                    <form method="post" action="${pageContext.request.contextPath}/admin/fee-schedule/${r.id}/expire" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                        <input type="hidden" name="terminationDate" value="${today}">
                        <button type="submit" class="btn-link btn-danger" onclick="return confirm('Expire this rate today?')">Expire</button>
                    </form>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty page.items}">
            <tr><td colspan="6" style="color:#9aa6b4;text-align:center;padding:24px">No rates on file.</td></tr>
        </c:if>
        </tbody>
    </table>

    <c:set var="baseUrl" value="${pageContext.request.contextPath}/admin/fee-schedule?planId=${plan.id}&"/>
    <jsp:include page="../../fragments/pagination.jsp"/>

    <p><a href="${pageContext.request.contextPath}/admin/plans/${plan.id}">&larr; Back to Plan</a></p>
</div>
</body>
</html>
