<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${empty rate ? 'Add Rate' : 'Edit Rate'} — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../../fragments/nav.jsp"/>
<div class="content" style="max-width:600px">
    <h1>${empty rate ? 'Add Fee Schedule Rate' : 'Edit Rate'}</h1>
    <p style="color:#6b7888">Plan: <c:out value="${plan.planName}"/></p>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <c:choose>
        <c:when test="${empty rate}">
            <form method="post" action="${pageContext.request.contextPath}/admin/fee-schedule/new">
        <input type="hidden" name="_csrf" value="${_csrf}">
            <input type="hidden" name="planId" value="${plan.id}">
        </c:when>
        <c:otherwise>
            <form method="post" action="${pageContext.request.contextPath}/admin/fee-schedule/${rate.id}/edit">
        <input type="hidden" name="_csrf" value="${_csrf}">
        </c:otherwise>
    </c:choose>

    <c:if test="${empty rate}">
        <div class="form-group">
            <label for="procedureCode">Procedure Code *</label>
            <input type="text" id="procedureCode" name="procedureCode" required maxlength="10">
            <small>CPT code entered by your administrator</small>
        </div>
        <div class="form-group">
            <label for="providerId">Provider (optional — leave blank for plan-wide rate)</label>
            <select id="providerId" name="providerId">
                <option value="">— Plan-wide —</option>
                <c:forEach var="p" items="${providers}">
                    <option value="${p.id}"><c:out value="${p.name}"/> (<c:out value="${p.npi}"/>)</option>
                </c:forEach>
            </select>
        </div>
    </c:if>

    <div class="form-group">
        <label for="allowedAmount">Allowed Amount ($) *</label>
        <input type="number" id="allowedAmount" name="allowedAmount" step="0.01" min="0" required
               value="<c:out value='${rate.allowedAmount}'/>">
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="effectiveDate">Effective Date *</label>
            <input type="date" id="effectiveDate" name="effectiveDate" required
                   value="<c:if test='${not empty rate.effectiveDate}'><fmt:formatDate value='${rate.effectiveDate}' pattern='yyyy-MM-dd'/></c:if>">
        </div>
        <div class="form-group">
            <label for="terminationDate">Termination Date</label>
            <input type="date" id="terminationDate" name="terminationDate"
                   value="<c:if test='${not empty rate.terminationDate}'><fmt:formatDate value='${rate.terminationDate}' pattern='yyyy-MM-dd'/></c:if>">
        </div>
    </div>

    <div style="display:flex;gap:10px;margin-top:8px">
        <button type="submit" class="btn btn-primary">${empty rate ? 'Add Rate' : 'Save Changes'}</button>
        <a href="${pageContext.request.contextPath}/admin/fee-schedule?planId=${plan.id}" class="btn">Cancel</a>
    </div>
    </form>
</div>
</body>
</html>
