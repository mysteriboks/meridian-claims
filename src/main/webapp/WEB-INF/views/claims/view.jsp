<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Claim <c:out value="${claim.claimNumber}"/> — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">

    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>
    <c:if test="${slaBreached}"><div class="alert alert-error">SLA threshold exceeded for status <c:out value="${claim.status}"/>.</div></c:if>

    <div style="display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:16px">
        <div>
            <h1 style="margin:0 0 4px"><c:out value="${claim.claimNumber}"/></h1>
            <span style="color:#6b7888;font-size:13px">
                <c:out value="${claim.claimType}"/> &nbsp;|&nbsp;
                Coverage: <c:out value="${claim.coverageOrder}"/>
            </span>
            &nbsp;<span class="badge badge-${claim.status == 'APPROVED' or claim.status == 'PAID' ? 'active' : claim.status == 'DENIED' or claim.status == 'VOIDED' ? 'inactive' : 'pending'}">
                <c:out value="${claim.status}"/>
            </span>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap">
            <%-- Re-adjudicate --%>
            <c:if test="${(claim.status == 'DENIED' or claim.status == 'IN_REVIEW')
                         and (currentUser.role == 'ADMIN' or currentUser.role == 'REVIEWER')}">
                <form method="post" action="${pageContext.request.contextPath}/claims/${claim.id}/readjudicate" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                    <button type="submit" class="btn" onclick="return confirm('Re-adjudicate this claim?')">Re-adjudicate</button>
                </form>
            </c:if>
            <%-- Resubmit (PENDING_INFO only) --%>
            <c:if test="${claim.status == 'PENDING_INFO'
                         and (currentUser.role == 'ADMIN' or currentUser.role == 'REVIEWER')}">
                <form method="post" action="${pageContext.request.contextPath}/claims/${claim.id}/resubmit" style="display:inline">
        <input type="hidden" name="_csrf" value="${_csrf}">
                    <button type="submit" class="btn" onclick="return confirm('Move claim back to IN_REVIEW?')">Resubmit</button>
                </form>
            </c:if>
        </div>
    </div>

    <%-- === Reviewer Actions === --%>
    <c:if test="${(currentUser.role == 'ADMIN' or currentUser.role == 'REVIEWER')
                 and (claim.status == 'IN_REVIEW' or claim.status == 'SUBMITTED')}">
    <h2 class="section-heading">Reviewer Actions</h2>
    <div style="display:flex;gap:12px;flex-wrap:wrap;margin-bottom:16px">

        <form method="post" action="${pageContext.request.contextPath}/claims/${claim.id}/approve" style="display:inline-flex;gap:4px;align-items:flex-start;flex-direction:column">
        <input type="hidden" name="_csrf" value="${_csrf}">
            <label style="font-size:12px;color:#6b7888">Approve — notes required</label>
            <div style="display:flex;gap:4px">
                <input type="text" name="notes" placeholder="Approval notes" required style="width:200px">
                <button type="submit" class="btn btn-primary" onclick="return confirm('Approve this claim?')">Approve</button>
            </div>
        </form>

        <form method="post" action="${pageContext.request.contextPath}/claims/${claim.id}/deny" style="display:inline-flex;gap:4px;align-items:flex-start;flex-direction:column">
        <input type="hidden" name="_csrf" value="${_csrf}">
            <label style="font-size:12px;color:#6b7888">Deny — reason + notes required</label>
            <div style="display:flex;gap:4px">
                <input type="text" name="denialReasonCode" placeholder="Reason code" required style="width:130px">
                <input type="text" name="notes" placeholder="Denial notes" required style="width:160px">
                <button type="submit" class="btn" style="background:#e74c3c;color:#fff" onclick="return confirm('Deny this claim?')">Deny</button>
            </div>
        </form>

        <form method="post" action="${pageContext.request.contextPath}/claims/${claim.id}/request-info" style="display:inline-flex;gap:4px;align-items:flex-start;flex-direction:column">
        <input type="hidden" name="_csrf" value="${_csrf}">
            <label style="font-size:12px;color:#6b7888">Request Info</label>
            <div style="display:flex;gap:4px;flex-wrap:wrap">
                <select name="requestedFrom" required style="width:120px">
                    <option value="">From…</option>
                    <option value="MEMBER">Member</option>
                    <option value="PROVIDER">Provider</option>
                    <option value="BOTH">Both</option>
                </select>
                <input type="date" name="dueDate" required style="width:140px">
                <input type="text" name="requestNotes" placeholder="Request details" required style="width:180px">
                <button type="submit" class="btn">Request Info</button>
            </div>
        </form>
    </div>
    </c:if>

    <%-- === Assignment === --%>
    <c:if test="${currentUser.role == 'ADMIN' or currentUser.role == 'REVIEWER'}">
    <h2 class="section-heading">Assignment</h2>
    <form method="post" action="${pageContext.request.contextPath}/claims/${claim.id}/assign" style="display:flex;gap:8px;align-items:center;margin-bottom:16px">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <span style="font-size:13px;color:#6b7888">Currently assigned to:
            <c:choose>
                <c:when test="${not empty claim.assignedToUserId}"><strong><c:out value="${claim.assignedToUserId}"/></strong></c:when>
                <c:otherwise><em>unassigned</em></c:otherwise>
            </c:choose>
        </span>
        <select name="reviewerId" style="width:220px">
            <option value="">— Unassign —</option>
            <c:forEach var="r" items="${reviewers}">
                <option value="${r.id}" ${claim.assignedToUserId == r.id ? 'selected' : ''}>
                    <c:out value="${r.fullName}"/> (<c:out value="${r.role}"/>)
                </option>
            </c:forEach>
        </select>
        <button type="submit" class="btn">Assign</button>
    </form>
    </c:if>

    <h2 class="section-heading">Claim Details</h2>
    <table class="data-table" style="max-width:700px">
        <tr><td style="color:#6b7888;width:200px">Member ID</td><td>
            <a href="${pageContext.request.contextPath}/members/<c:out value='${claim.memberId}'/>"><c:out value="${claim.memberId}"/></a>
        </td></tr>
        <tr><td style="color:#6b7888">Provider ID</td><td><c:out value="${claim.providerId}"/></td></tr>
        <tr><td style="color:#6b7888">Date of Service</td><td><c:out value="${claim.dateOfService}"/></td></tr>
        <tr><td style="color:#6b7888">Submission Date</td><td><c:out value="${claim.submissionDate}"/></td></tr>
        <tr><td style="color:#6b7888">Plan ID</td><td><c:out value="${claim.planId}"/></td></tr>
        <tr><td style="color:#6b7888">Status entered at</td><td><fmt:formatDate value="${claim.statusEnteredAt}" pattern="yyyy-MM-dd HH:mm"/></td></tr>
        <c:if test="${claim.coverageOrder == 'SECONDARY'}">
            <tr><td style="color:#6b7888">Primary Paid (COB)</td><td>$<c:out value="${claim.cobPrimaryPaid}"/></td></tr>
        </c:if>
        <c:if test="${not empty claim.denialReasonCode}">
            <tr><td style="color:#6b7888">Denial Reason</td>
                <td><span class="badge badge-inactive"><c:out value="${claim.denialReasonCode}"/></span></td></tr>
        </c:if>
        <c:if test="${not empty claim.notes}">
            <tr><td style="color:#6b7888">Notes</td><td><c:out value="${claim.notes}"/></td></tr>
        </c:if>
    </table>

    <h2 class="section-heading">Diagnoses</h2>
    <table class="data-table">
        <thead><tr><th>#</th><th>Type</th><th>Code</th></tr></thead>
        <tbody>
        <c:forEach var="d" items="${diagnoses}">
            <tr>
                <td><c:out value="${d.sequenceNumber}"/></td>
                <td><c:out value="${d.diagnosisType}"/></td>
                <td><c:out value="${d.diagnosisCode}"/></td>
            </tr>
        </c:forEach>
        <c:if test="${empty diagnoses}">
            <tr><td colspan="3" style="color:#9aa6b4;text-align:center;padding:16px">No diagnoses.</td></tr>
        </c:if>
        </tbody>
    </table>

    <h2 class="section-heading">Line Items</h2>
    <table class="data-table">
        <thead>
            <tr>
                <th>Procedure</th><th>Billed</th><th>Allowed</th><th>Rate Source</th>
                <th>Deductible</th><th>Copay</th><th>Plan Paid</th><th>Member Resp.</th>
            </tr>
        </thead>
        <tbody>
        <c:forEach var="li" items="${lineItems}">
            <tr>
                <td><c:out value="${li.procedureCode}"/>
                    <c:if test="${not empty li.description}">
                        <br><small style="color:#9aa6b4"><c:out value="${li.description}"/></small>
                    </c:if>
                </td>
                <td>$<c:out value="${li.billedAmount}"/></td>
                <td><c:choose>
                    <c:when test="${not empty li.allowedAmount}">$<c:out value="${li.allowedAmount}"/></c:when>
                    <c:otherwise><span style="color:#9aa6b4">—</span></c:otherwise>
                </c:choose></td>
                <td><c:choose>
                    <c:when test="${li.rateSource == 'NO_RATE'}"><span class="badge badge-inactive">NO_RATE</span></c:when>
                    <c:otherwise><c:out value="${li.rateSource}"/></c:otherwise>
                </c:choose></td>
                <td>$<c:out value="${li.deductibleApplied}"/></td>
                <td>$<c:out value="${li.copayApplied}"/></td>
                <td><c:choose>
                    <c:when test="${not empty li.planPaidAmount}">$<c:out value="${li.planPaidAmount}"/></c:when>
                    <c:otherwise><span style="color:#9aa6b4">—</span></c:otherwise>
                </c:choose></td>
                <td><c:choose>
                    <c:when test="${not empty li.memberResponsibility}">$<c:out value="${li.memberResponsibility}"/></c:when>
                    <c:otherwise><span style="color:#9aa6b4">—</span></c:otherwise>
                </c:choose></td>
            </tr>
        </c:forEach>
        <c:if test="${empty lineItems}">
            <tr><td colspan="8" style="color:#9aa6b4;text-align:center;padding:16px">No line items.</td></tr>
        </c:if>
        </tbody>
    </table>

    <%-- === Info Requests === --%>
    <h2 class="section-heading">Info Requests</h2>
    <c:forEach var="ir" items="${infoRequests}">
    <div style="border:1px solid #dde3ec;border-radius:6px;padding:12px;margin-bottom:10px">
        <div style="display:flex;justify-content:space-between;align-items:flex-start">
            <div>
                <strong>From: <c:out value="${ir.requestedFrom}"/></strong> &nbsp;
                <span class="badge badge-${ir.status == 'OPEN' ? 'pending' : 'active'}"><c:out value="${ir.status}"/></span>
                &nbsp; Due: <c:out value="${ir.dueDate}"/>
            </div>
        </div>
        <p style="margin:6px 0 4px;font-size:13px"><c:out value="${ir.requestNotes}"/></p>
        <c:if test="${not empty ir.responseNotes}">
            <p style="margin:4px 0;font-size:13px;color:#3a7d44"><strong>Response:</strong> <c:out value="${ir.responseNotes}"/></p>
        </c:if>
        <c:if test="${ir.status == 'OPEN'}">
            <form method="post" action="${pageContext.request.contextPath}/claims/info-requests/${ir.id}/respond" style="display:flex;gap:6px;margin-top:8px">
        <input type="hidden" name="_csrf" value="${_csrf}">
                <input type="hidden" name="claimId" value="${claim.id}">
                <input type="text" name="responseNotes" placeholder="Response notes" required style="flex:1">
                <button type="submit" class="btn btn-primary">Record Response</button>
            </form>
        </c:if>
    </div>
    </c:forEach>
    <c:if test="${empty infoRequests}">
        <p style="color:#9aa6b4;font-size:13px">No info requests.</p>
    </c:if>

    <%-- === Notes === --%>
    <h2 class="section-heading">Notes</h2>
    <c:forEach var="n" items="${notes}">
        <div style="border-left:3px solid #4a90e2;padding:6px 12px;margin-bottom:8px;font-size:13px">
            <span style="color:#6b7888">User <c:out value="${n.authorUserId}"/> &nbsp;
            <fmt:formatDate value="${n.createdAt}" pattern="yyyy-MM-dd HH:mm"/></span>
            <p style="margin:4px 0"><c:out value="${n.note}"/></p>
        </div>
    </c:forEach>
    <form method="post" action="${pageContext.request.contextPath}/claims/${claim.id}/notes" style="display:flex;gap:8px;margin-top:8px">
        <input type="hidden" name="_csrf" value="${_csrf}">
        <input type="text" name="note" placeholder="Add a note…" required style="flex:1">
        <button type="submit" class="btn">Add Note</button>
    </form>

    <h2 class="section-heading">Adjudication Results</h2>
    <table class="data-table">
        <thead><tr><th>Run</th><th>Step</th><th>Rule</th><th>Type</th><th>Result</th><th>Reason</th></tr></thead>
        <tbody>
        <c:forEach var="ar" items="${adjudicationResults}">
            <tr>
                <td><c:out value="${ar.runId}"/></td>
                <td><c:out value="${ar.stepNumber}"/></td>
                <td><c:out value="${ar.ruleName}"/></td>
                <td><c:out value="${ar.ruleType}"/></td>
                <td><span class="badge badge-${ar.passed ? 'active' : 'inactive'}"><c:out value="${ar.passed ? 'PASS' : 'FAIL'}"/></span></td>
                <td style="font-size:12px"><c:out value="${ar.reason}"/></td>
            </tr>
        </c:forEach>
        <c:if test="${empty adjudicationResults}">
            <tr><td colspan="6" style="color:#9aa6b4;text-align:center;padding:16px">No adjudication results.</td></tr>
        </c:if>
        </tbody>
    </table>

    <h2 class="section-heading">Audit Trail</h2>
    <table class="data-table">
        <thead><tr><th>Event</th><th>Old Status</th><th>New Status</th><th>By</th><th>Notes</th><th>When</th></tr></thead>
        <tbody>
        <c:forEach var="a" items="${auditTrail}">
            <tr>
                <td><c:out value="${a.eventType}"/></td>
                <td><c:out value="${a.oldStatus}"/></td>
                <td><c:out value="${a.newStatus}"/></td>
                <td><c:out value="${a.changedByUserId}"/></td>
                <td style="font-size:12px"><c:out value="${a.notes}"/></td>
                <td style="font-size:12px"><fmt:formatDate value="${a.changedAt}" pattern="yyyy-MM-dd HH:mm"/></td>
            </tr>
        </c:forEach>
        <c:if test="${empty auditTrail}">
            <tr><td colspan="6" style="color:#9aa6b4;text-align:center;padding:16px">No audit entries.</td></tr>
        </c:if>
        </tbody>
    </table>

    <p style="margin-top:20px"><a href="${pageContext.request.contextPath}/claims">&larr; Back to Claims</a></p>
</div>
</body>
</html>
