<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Admin Operations — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <h1>Admin Operations</h1>
    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <h2 class="section-heading">Bulk Re-adjudication</h2>
    <p style="color:#6b7888;font-size:13px">Re-adjudicates all claims in a given status. Use after a plan rule change affecting historical claims.</p>
    <form method="post" action="${pageContext.request.contextPath}/admin/operations/bulk-readjudicate"
          style="display:flex;gap:8px;align-items:flex-end">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <div>
            <label style="font-size:12px;color:#6b7888">Status to reprocess</label><br>
            <select name="status">
                <option value="DENIED">DENIED</option>
                <option value="IN_REVIEW">IN_REVIEW</option>
            </select>
        </div>
        <button type="submit" class="btn btn-primary"
                onclick="return confirm('Bulk re-adjudicate all claims in the selected status? This may take several minutes.')">
            Run Bulk Re-adjudication
        </button>
    </form>

    <h2 class="section-heading" style="margin-top:32px">Manually Trigger a Scheduled Job</h2>
    <form method="post" action="${pageContext.request.contextPath}/admin/operations/trigger-job"
          style="display:flex;gap:8px;align-items:flex-end">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <div>
            <label style="font-size:12px;color:#6b7888">Job</label><br>
            <select name="jobName">
                <c:forEach var="jn" items="${triggerableJobs}">
                    <option value="${jn}"><c:out value="${jn}"/></option>
                </c:forEach>
            </select>
        </div>
        <button type="submit" class="btn" onclick="return confirm('Trigger this job now?')">Run Now</button>
    </form>

    <p style="margin-top:16px"><a href="${pageContext.request.contextPath}/admin/operations/archive" class="btn-link">Search Archived Claims &rarr;</a></p>

    <h2 class="section-heading" style="margin-top:32px">Recent Scheduled Job Runs</h2>
    <table class="data-table">
        <thead><tr><th>Job</th><th>Started</th><th>Completed</th><th>Status</th><th>Records</th><th>Error</th></tr></thead>
        <tbody>
        <c:forEach var="j" items="${recentJobs}">
            <tr>
                <td><c:out value="${j.jobName}"/></td>
                <td><fmt:formatDate value="${j.startedAt}" pattern="yyyy-MM-dd HH:mm:ss"/></td>
                <td><fmt:formatDate value="${j.completedAt}" pattern="yyyy-MM-dd HH:mm:ss"/></td>
                <td><span class="badge badge-${j.status == 'SUCCESS' ? 'active' : j.status == 'RUNNING' ? 'pending' : 'inactive'}"><c:out value="${j.status}"/></span></td>
                <td><c:out value="${j.recordsProcessed}"/></td>
                <td style="font-size:12px;color:#e74c3c"><c:out value="${j.errorMessage}"/></td>
            </tr>
        </c:forEach>
        <c:if test="${empty recentJobs}">
            <tr><td colspan="6" style="text-align:center;color:#9aa6b4;padding:16px">No job history yet.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>
</body>
</html>
