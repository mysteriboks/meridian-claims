<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title><c:out value="${plan.planName}"/> — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../../fragments/nav.jsp"/>
<div class="content">
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <div style="display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:16px">
        <div>
            <h1 style="margin:0 0 4px"><c:out value="${plan.planName}"/></h1>
            <span style="color:#6b7888;font-size:13px"><c:out value="${plan.planType}"/></span>
        </div>
        <div style="display:flex;gap:8px">
            <a class="btn" href="${pageContext.request.contextPath}/admin/plans/${plan.id}/edit">Edit</a>
            <a class="btn" href="${pageContext.request.contextPath}/admin/fee-schedule?planId=${plan.id}">Fee Schedule</a>
            <form method="post" action="${pageContext.request.contextPath}/admin/plans/${plan.id}/deactivate" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                <button type="submit" class="btn btn-danger" onclick="return confirm('Deactivate plan?')">Deactivate</button>
            </form>
        </div>
    </div>

    <h2 class="section-heading">Plan Details</h2>
    <table class="data-table" style="max-width:600px">
        <tr><td style="color:#6b7888;width:200px">Deductible</td><td>$<c:out value="${plan.deductibleAmount}"/></td></tr>
        <tr><td style="color:#6b7888">OOP Max</td><td>$<c:out value="${plan.oopMax}"/></td></tr>
        <tr><td style="color:#6b7888">Copay</td><td>$<c:out value="${plan.copayAmount}"/></td></tr>
        <tr><td style="color:#6b7888">In-Network Coverage</td><td><c:out value="${plan.coveragePctInNetwork}"/>%</td></tr>
        <tr><td style="color:#6b7888">Out-of-Network Coverage</td><td><c:out value="${plan.coveragePctOutNetwork}"/>%</td></tr>
        <tr><td style="color:#6b7888">Benefit Year Start</td><td><fmt:formatDate value="${plan.benefitYearStart}" pattern="MMM d"/></td></tr>
        <tr><td style="color:#6b7888">Timely Filing</td><td><c:out value="${plan.timelyFilingDays}"/> days</td></tr>
    </table>

    <h2 class="section-heading">Coverage Rules by Service Type</h2>
    <table class="data-table">
        <thead>
            <tr><th>Service Type</th><th>Coverage %</th><th>Requires Referral</th><th>Requires Prior Auth</th><th>Actions</th></tr>
        </thead>
        <tbody>
        <c:forEach var="rule" items="${coverageRules}">
            <tr>
                <td><c:out value="${rule.serviceType}"/></td>
                <td><c:out value="${rule.coveragePct}"/>%</td>
                <td>${rule.requiresReferral ? 'Yes' : 'No'}</td>
                <td>${rule.requiresPriorAuth ? 'Yes' : 'No'}</td>
                <td>
                    <form method="post" action="${pageContext.request.contextPath}/admin/plans/${plan.id}/rules/${rule.id}/delete" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                        <button type="submit" class="btn-link btn-danger" onclick="return confirm('Remove rule?')">Remove</button>
                    </form>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty coverageRules}">
            <tr><td colspan="5" style="color:#9aa6b4;text-align:center;padding:16px">No rules configured.</td></tr>
        </c:if>
        </tbody>
    </table>

    <h2 class="section-heading">Add Coverage Rule</h2>
    <form method="post" action="${pageContext.request.contextPath}/admin/plans/${plan.id}/rules/save"
          style="display:flex;gap:12px;flex-wrap:wrap;align-items:flex-end">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <div class="form-group" style="margin:0">
            <label>Service Type</label>
            <select name="serviceType">
                <c:forEach var="st" items="${serviceTypes}">
                    <option value="${st.code}"><c:out value="${st.description}"/></option>
                </c:forEach>
            </select>
        </div>
        <div class="form-group" style="margin:0">
            <label>Coverage %</label>
            <input type="number" name="coveragePct" step="0.01" min="0" max="100" value="80.00" style="width:90px">
        </div>
        <div class="form-group" style="margin:0">
            <label><input type="checkbox" name="requiresReferral" value="true"> Requires Referral</label>
        </div>
        <div class="form-group" style="margin:0">
            <label><input type="checkbox" name="requiresPriorAuth" value="true"> Requires Prior Auth</label>
        </div>
        <button type="submit" class="btn btn-primary">Save Rule</button>
    </form>

    <p style="margin-top:20px"><a href="${pageContext.request.contextPath}/admin/plans">&larr; Back to Plans</a></p>
</div>
</body>
</html>
