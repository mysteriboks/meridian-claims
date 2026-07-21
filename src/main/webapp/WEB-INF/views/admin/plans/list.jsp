<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Plans — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../../fragments/nav.jsp"/>
<div class="content">
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px">
        <h1 style="margin:0">Insurance Plans</h1>
        <a class="btn btn-primary" href="${pageContext.request.contextPath}/admin/plans/new">+ New Plan</a>
    </div>
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>

    <table class="data-table">
        <thead>
            <tr><th>Plan Name</th><th>Type</th><th>Deductible</th><th>OOP Max</th><th>Copay</th><th>In-Net %</th><th>Filing Days</th><th>Actions</th></tr>
        </thead>
        <tbody>
        <c:forEach var="p" items="${page.items}">
            <tr>
                <td><a href="${pageContext.request.contextPath}/admin/plans/${p.id}"><c:out value="${p.planName}"/></a></td>
                <td><c:out value="${p.planType}"/></td>
                <td>$<c:out value="${p.deductibleAmount}"/></td>
                <td>$<c:out value="${p.oopMax}"/></td>
                <td>$<c:out value="${p.copayAmount}"/></td>
                <td><c:out value="${p.coveragePctInNetwork}"/>%</td>
                <td><c:out value="${p.timelyFilingDays}"/></td>
                <td>
                    <a href="${pageContext.request.contextPath}/admin/plans/${p.id}/edit" class="btn-link">Edit</a>
                    <a href="${pageContext.request.contextPath}/admin/fee-schedule?planId=${p.id}" class="btn-link">Fee Schedule</a>
                    <form method="post" action="${pageContext.request.contextPath}/admin/plans/${p.id}/deactivate" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                        <button type="submit" class="btn-link btn-danger" onclick="return confirm('Deactivate plan?')">Deactivate</button>
                    </form>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty page.items}">
            <tr><td colspan="8" style="color:#9aa6b4;text-align:center;padding:24px">No plans configured yet.</td></tr>
        </c:if>
        </tbody>
    </table>

    <c:set var="baseUrl" value="${pageContext.request.contextPath}/admin/plans?"/>
    <jsp:include page="../../fragments/pagination.jsp"/>
</div>
</body>
</html>
