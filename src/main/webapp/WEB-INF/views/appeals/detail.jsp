<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Appeal #<c:out value="${appeal.id}"/> — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
        <h1 style="margin:0">Appeal #<c:out value="${appeal.id}"/></h1>
        <span class="badge badge-${appeal.status == 'OPEN' ? 'pending' : appeal.status == 'APPROVED' ? 'active' : 'inactive'}">
            <c:out value="${appeal.status}"/>
        </span>
    </div>

    <table class="data-table" style="max-width:600px;margin-bottom:24px">
        <tr><td style="color:#6b7888">Claim</td><td><a href="${pageContext.request.contextPath}/claims/${appeal.claimId}"><c:out value="${appeal.claimId}"/></a></td></tr>
        <tr><td style="color:#6b7888">Type</td><td><c:out value="${appeal.appealType}"/></td></tr>
        <tr><td style="color:#6b7888">Submitted</td><td><fmt:formatDate value="${appeal.submittedDate}" pattern="yyyy-MM-dd"/></td></tr>
        <tr><td style="color:#6b7888">Deadline</td><td><fmt:formatDate value="${appeal.deadlineDate}" pattern="yyyy-MM-dd"/></td></tr>
        <c:if test="${not empty appeal.outcomeNotes}">
            <tr><td style="color:#6b7888">Outcome Notes</td><td><c:out value="${appeal.outcomeNotes}"/></td></tr>
        </c:if>
    </table>

    <c:if test="${appeal.status == 'OPEN'}">
    <div style="display:flex;gap:12px;flex-wrap:wrap">
        <form method="post" action="${pageContext.request.contextPath}/appeals/${appeal.id}/approve" style="display:flex;gap:6px;flex-direction:column">
        <input type="hidden" name="_csrf" value="${_csrf}">
            <label style="font-size:12px;color:#6b7888">Approve — notes required</label>
            <div style="display:flex;gap:6px">
                <input type="text" name="outcomeNotes" placeholder="Outcome notes" required style="width:200px">
                <button type="submit" class="btn btn-primary" onclick="return confirm('Approve this appeal and trigger re-adjudication?')">Approve</button>
            </div>
        </form>
        <form method="post" action="${pageContext.request.contextPath}/appeals/${appeal.id}/deny" style="display:flex;gap:6px;flex-direction:column">
        <input type="hidden" name="_csrf" value="${_csrf}">
            <label style="font-size:12px;color:#6b7888">Deny — notes required</label>
            <div style="display:flex;gap:6px">
                <input type="text" name="outcomeNotes" placeholder="Denial reason" required style="width:200px">
                <button type="submit" class="btn" style="background:#e74c3c;color:#fff" onclick="return confirm('Deny this appeal?')">Deny</button>
            </div>
        </form>
        <form method="post" action="${pageContext.request.contextPath}/appeals/${appeal.id}/withdraw">
        <input type="hidden" name="_csrf" value="${_csrf}">
            <button type="submit" class="btn" onclick="return confirm('Withdraw this appeal?')">Withdraw</button>
        </form>
    </div>
    </c:if>

    <p style="margin-top:20px"><a href="${pageContext.request.contextPath}/appeals">&larr; Back to Appeals</a></p>
</div>
</body>
</html>
