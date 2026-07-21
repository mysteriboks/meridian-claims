<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Meridian Claims — Dashboard</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
    <style>
        .stat-grid { display:flex; gap:16px; flex-wrap:wrap; margin-bottom:24px }
        .stat-card { background:#fff; border:1px solid #dde3ec; border-radius:8px; padding:16px 20px; min-width:160px; flex:1 }
        .stat-label { display:block; font-size:12px; color:#6b7888; margin-bottom:4px }
        .stat-value { display:block; font-size:28px; font-weight:700; color:#1a2332 }
        .stat-value.breach { color:#e74c3c }
        .stat-foot { display:block; font-size:11px; color:#9aa6b4; margin-top:4px }
        @media print { .action-grid,.search-bar,form,button,.btn,.btn-link { display:none } }
    </style>
</head>
<body>
<jsp:include page="fragments/nav.jsp"/>
<div class="content">

    <c:if test="${param.passwordChanged != null}">
        <div class="alert alert-success">Your password has been updated.</div>
    </c:if>

    <section class="hero">
        <div>
            <h1 class="hero-title">Welcome back, <c:out value="${sessionScope.MERIDIAN_USER.fullName}"/></h1>
            <p class="hero-sub">
                Signed in as <span class="role-pill"><c:out value="${sessionScope.MERIDIAN_USER.role}"/></span>
                <c:if test="${not empty sessionScope.MERIDIAN_USER.lastLoginAt}">
                    · Last sign-in <fmt:formatDate value="${sessionScope.MERIDIAN_USER.lastLoginAt}" pattern="MMM d, yyyy 'at' h:mm a"/>
                </c:if>
            </p>
        </div>
    </section>

    <%-- Live stat tiles --%>
    <section class="stat-grid">
        <div class="stat-card">
            <span class="stat-label">Submitted Today</span>
            <span class="stat-value"><c:out value="${todayCount}"/></span>
        </div>
        <div class="stat-card">
            <span class="stat-label">This Week</span>
            <span class="stat-value"><c:out value="${weekCount}"/></span>
        </div>
        <c:if test="${not empty myQueueSize}">
        <div class="stat-card">
            <span class="stat-label">My Queue (IN_REVIEW)</span>
            <span class="stat-value"><c:out value="${myQueueSize}"/></span>
        </div>
        </c:if>
        <c:if test="${not empty slaBreachCount}">
        <div class="stat-card">
            <span class="stat-label">SLA Breaches</span>
            <span class="stat-value ${slaBreachCount > 0 ? 'breach' : ''}"><c:out value="${slaBreachCount}"/></span>
        </div>
        </c:if>
        <c:if test="${not empty paymentTotals.this_month}">
        <div class="stat-card">
            <span class="stat-label">Paid This Month</span>
            <span class="stat-value">$<fmt:formatNumber value="${paymentTotals.this_month}" pattern="#,##0.00"/></span>
            <c:if test="${not empty paymentTotals.last_month}">
            <span class="stat-foot">Last month: $<fmt:formatNumber value="${paymentTotals.last_month}" pattern="#,##0.00"/></span>
            </c:if>
        </div>
        </c:if>
    </section>

    <%-- Claims by status --%>
    <c:if test="${not empty claimsByStatus}">
    <h2 class="section-heading">Claims by Status</h2>
    <section class="stat-grid">
        <c:forEach var="entry" items="${claimsByStatus}">
        <div class="stat-card">
            <span class="stat-label"><c:out value="${entry.key}"/></span>
            <span class="stat-value" style="font-size:20px"><c:out value="${entry.value}"/></span>
        </div>
        </c:forEach>
    </section>
    </c:if>

    <%-- Top denial reasons --%>
    <c:if test="${not empty topDenialReasons}">
    <h2 class="section-heading">Top Denial Reasons (last 30 days)</h2>
    <table class="data-table" style="max-width:600px;margin-bottom:24px">
        <thead><tr><th>Reason Code</th><th>Description</th><th>Count</th></tr></thead>
        <tbody>
        <c:forEach var="r" items="${topDenialReasons}">
            <tr>
                <td><c:out value="${r.denial_reason_code}"/></td>
                <td><c:out value="${r.description}"/></td>
                <td><c:out value="${r.cnt}"/></td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
    </c:if>

    <%-- Recent activity --%>
    <c:if test="${not empty recentActivity}">
    <h2 class="section-heading">Recent Activity</h2>
    <table class="data-table" style="margin-bottom:24px">
        <thead><tr><th>Claim</th><th>Event</th><th>Old Status</th><th>New Status</th><th>When</th></tr></thead>
        <tbody>
        <c:forEach var="a" items="${recentActivity}">
            <tr>
                <td><a href="${pageContext.request.contextPath}/claims/${a.claim_id}"><c:out value="${a.claim_number}"/></a></td>
                <td><c:out value="${a.event_type}"/></td>
                <td><c:out value="${a.old_status}"/></td>
                <td><c:out value="${a.new_status}"/></td>
                <td style="font-size:12px"><fmt:formatDate value="${a.changed_at}" pattern="MM/dd HH:mm"/></td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
    </c:if>

    <%-- Quick actions --%>
    <h2 class="section-heading">Quick Actions</h2>
    <section class="action-grid">
        <c:if test="${sessionScope.MERIDIAN_USER.role == 'ADMIN'}">
            <a class="action-card" href="${pageContext.request.contextPath}/admin/users"><span class="action-title">User Management</span><span class="action-desc">Create, edit, lock, and reset staff accounts.</span></a>
        </c:if>
        <a class="action-card" href="${pageContext.request.contextPath}/claims/new"><span class="action-title">Submit a Claim</span><span class="action-desc">Intake a new claim for adjudication.</span></a>
        <a class="action-card" href="${pageContext.request.contextPath}/claims?status=IN_REVIEW"><span class="action-title">Review Queue</span><span class="action-desc">Work claims assigned to you and the shared pool.</span></a>
        <a class="action-card" href="${pageContext.request.contextPath}/reports"><span class="action-title">Reports</span><span class="action-desc">Claims summary, denials, payments, SLA performance, and more.</span></a>
        <a class="action-card" href="${pageContext.request.contextPath}/appeals"><span class="action-title">Appeals</span><span class="action-desc">Work the open appeals queue.</span></a>
        <a class="action-card" href="${pageContext.request.contextPath}/members"><span class="action-title">Members</span><span class="action-desc">Manage member coverage and enrollment records.</span></a>
        <a class="action-card" href="${pageContext.request.contextPath}/providers"><span class="action-title">Providers</span><span class="action-desc">Manage provider records, NPI, and network status.</span></a>
    </section>
</div>
</body>
</html>
