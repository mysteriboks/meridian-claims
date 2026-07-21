<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${empty plan ? 'New Plan' : 'Edit Plan'} — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../../fragments/nav.jsp"/>
<div class="content" style="max-width:760px">
    <h1>${empty plan ? 'New Plan' : 'Edit Plan'}</h1>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <c:choose>
        <c:when test="${empty plan}">
            <form method="post" action="${pageContext.request.contextPath}/admin/plans/new">
        <input type="hidden" name="_csrf" value="${_csrf}">
        </c:when>
        <c:otherwise>
            <form method="post" action="${pageContext.request.contextPath}/admin/plans/${plan.id}/edit">
        <input type="hidden" name="_csrf" value="${_csrf}">
        </c:otherwise>
    </c:choose>

    <div style="display:grid;grid-template-columns:2fr 1fr;gap:16px">
        <div class="form-group">
            <label for="planName">Plan Name *</label>
            <input type="text" id="planName" name="planName" required maxlength="200"
                   value="<c:out value='${plan.planName}'/>">
        </div>
        <div class="form-group">
            <label for="planType">Plan Type *</label>
            <select id="planType" name="planType">
                <c:forEach var="t" items="${planTypes}">
                    <option value="${t}" ${plan.planType == t ? 'selected' : ''}><c:out value="${t}"/></option>
                </c:forEach>
            </select>
        </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="deductibleAmount">Deductible ($) *</label>
            <input type="number" id="deductibleAmount" name="deductibleAmount" step="0.01" min="0" required
                   value="<c:out value='${plan.deductibleAmount}'/>">
        </div>
        <div class="form-group">
            <label for="oopMax">OOP Max ($) *</label>
            <input type="number" id="oopMax" name="oopMax" step="0.01" min="0" required
                   value="<c:out value='${plan.oopMax}'/>">
        </div>
        <div class="form-group">
            <label for="copayAmount">Copay ($) *</label>
            <input type="number" id="copayAmount" name="copayAmount" step="0.01" min="0" required
                   value="<c:out value='${plan.copayAmount}'/>">
        </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="coveragePctInNetwork">In-Network Coverage % *</label>
            <input type="number" id="coveragePctInNetwork" name="coveragePctInNetwork" step="0.01" min="0" max="100" required
                   value="<c:out value='${plan.coveragePctInNetwork}'/>">
        </div>
        <div class="form-group">
            <label for="coveragePctOutNetwork">Out-of-Network Coverage % *</label>
            <input type="number" id="coveragePctOutNetwork" name="coveragePctOutNetwork" step="0.01" min="0" max="100" required
                   value="<c:out value='${plan.coveragePctOutNetwork}'/>">
        </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="benefitYearStart">Benefit Year Start *</label>
            <input type="date" id="benefitYearStart" name="benefitYearStart" required
                   value="<c:if test='${not empty plan.benefitYearStart}'><fmt:formatDate value='${plan.benefitYearStart}' pattern='yyyy-MM-dd'/></c:if>">
        </div>
        <div class="form-group">
            <label for="timelyFilingDays">Timely Filing Days *</label>
            <input type="number" id="timelyFilingDays" name="timelyFilingDays" min="1" required
                   value="<c:out value='${not empty plan.timelyFilingDays ? plan.timelyFilingDays : 180}'/>">
        </div>
    </div>

    <div style="display:flex;gap:10px;margin-top:8px">
        <button type="submit" class="btn btn-primary">${empty plan ? 'Create Plan' : 'Save Changes'}</button>
        <c:choose>
            <c:when test="${not empty plan}">
                <a href="${pageContext.request.contextPath}/admin/plans/${plan.id}" class="btn">Cancel</a>
            </c:when>
            <c:otherwise>
                <a href="${pageContext.request.contextPath}/admin/plans" class="btn">Cancel</a>
            </c:otherwise>
        </c:choose>
    </div>
    </form>
</div>
</body>
</html>
