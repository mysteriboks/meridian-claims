<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Payment Queue — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <h1>Payment Queue</h1>
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <table class="data-table">
        <thead>
            <tr><th>ID</th><th>Claim ID</th><th>Plan Paid Total</th><th>Member Resp.</th><th>Status</th><th>Actions</th></tr>
        </thead>
        <tbody>
        <c:forEach var="p" items="${pendingPayments}">
            <tr>
                <td><c:out value="${p.id}"/></td>
                <td><a href="${pageContext.request.contextPath}/claims/${p.claimId}"><c:out value="${p.claimId}"/></a></td>
                <td>$<c:out value="${p.planPaidTotal}"/></td>
                <td>$<c:out value="${p.memberResponsibility}"/></td>
                <td><span class="badge badge-pending"><c:out value="${p.status}"/></span></td>
                <td>
                    <a href="${pageContext.request.contextPath}/finance/payments/${p.id}" class="btn-link">Record Payment</a>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty pendingPayments}">
            <tr><td colspan="6" style="text-align:center;color:#9aa6b4;padding:24px">No pending payments.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>
</body>
</html>
