<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Subrogation Case #<c:out value="${subroCase.id}"/> — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <h1>Subrogation Case #<c:out value="${subroCase.id}"/></h1>
    <table class="data-table" style="max-width:500px;margin-bottom:24px">
        <tr><td style="color:#6b7888">Claim</td><td><a href="${pageContext.request.contextPath}/claims/${subroCase.claimId}"><c:out value="${subroCase.claimId}"/></a></td></tr>
        <tr><td style="color:#6b7888">Opened</td><td><c:out value="${subroCase.openedDate}"/></td></tr>
        <tr><td style="color:#6b7888">Status</td><td><c:out value="${subroCase.status}"/></td></tr>
        <c:if test="${not empty subroCase.liableParty}">
            <tr><td style="color:#6b7888">Liable Party</td><td><c:out value="${subroCase.liableParty}"/></td></tr>
        </c:if>
        <c:if test="${not empty subroCase.recoveryAmount}">
            <tr><td style="color:#6b7888">Recovery Amount</td><td>$<c:out value="${subroCase.recoveryAmount}"/></td></tr>
        </c:if>
        <c:if test="${not empty subroCase.notes}">
            <tr><td style="color:#6b7888">Notes</td><td><c:out value="${subroCase.notes}"/></td></tr>
        </c:if>
    </table>

    <c:if test="${subroCase.status == 'OPEN'}">
    <h2 class="section-heading">Record Recovery</h2>
    <form method="post" action="${pageContext.request.contextPath}/finance/subrogation/${subroCase.id}/recover"
          style="display:flex;flex-direction:column;gap:10px;max-width:400px">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <div><label>Liable Party *</label><br><input type="text" name="liableParty" required style="width:100%"></div>
        <div><label>Recovery Amount *</label><br><input type="text" name="recoveryAmount" required style="width:100%"></div>
        <div><label>Notes</label><br><input type="text" name="notes" style="width:100%"></div>
        <button type="submit" class="btn btn-primary" onclick="return confirm('Record this recovery?')">Record Recovery</button>
    </form>
    </c:if>

    <p style="margin-top:20px"><a href="${pageContext.request.contextPath}/finance/subrogation">&larr; Back to Subrogation</a></p>
</div>
</body>
</html>
