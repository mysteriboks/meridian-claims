<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Reports — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
    <style>@media print { nav,.btn { display:none } }</style>
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <h1>Reports</h1>
    <p style="color:#6b7888">All reports are filterable, paginated, and exportable to CSV.</p>

    <h2 class="section-heading">Claims</h2>
    <section class="action-grid">
        <a class="action-card" href="${pageContext.request.contextPath}/reports/claims-summary"><span class="action-title">Claims Summary</span><span class="action-desc">Count and totals by status, date range, plan.</span></a>
        <a class="action-card" href="${pageContext.request.contextPath}/reports/claims-detail"><span class="action-title">Claims Detail</span><span class="action-desc">Full claim list with all fields, filterable.</span></a>
        <a class="action-card" href="${pageContext.request.contextPath}/reports/denials"><span class="action-title">Denial Report</span><span class="action-desc">Denials by reason code with trend over time.</span></a>
        <a class="action-card" href="${pageContext.request.contextPath}/reports/adjudication-rules"><span class="action-title">Adjudication Rules</span><span class="action-desc">Rule fire rates and denial percentages.</span></a>
        <a class="action-card" href="${pageContext.request.contextPath}/reports/cob"><span class="action-title">COB Report</span><span class="action-desc">Secondary-coverage claims, primary vs. secondary liability.</span></a>
        <a class="action-card" href="${pageContext.request.contextPath}/reports/fee-schedule-coverage"><span class="action-title">Fee Schedule Coverage</span><span class="action-desc">Procedure codes billed with no rate on file.</span></a>
    </section>

    <h2 class="section-heading">Financial</h2>
    <section class="action-grid">
        <a class="action-card" href="${pageContext.request.contextPath}/reports/payments"><span class="action-title">Payment Report</span><span class="action-desc">Payments by date, plan, provider; batch status.</span></a>
        <a class="action-card" href="${pageContext.request.contextPath}/reports/subrogation"><span class="action-title">Subrogation Report</span><span class="action-desc">Open cases, recovery amounts, liable parties.</span></a>
    </section>

    <h2 class="section-heading">Workflow</h2>
    <section class="action-grid">
        <a class="action-card" href="${pageContext.request.contextPath}/reports/appeals"><span class="action-title">Appeals Report</span><span class="action-desc">Open/closed appeals, resolution rate, avg time.</span></a>
        <a class="action-card" href="${pageContext.request.contextPath}/reports/sla-performance"><span class="action-title">SLA Performance</span><span class="action-desc">Average time in status, breach rate by reviewer.</span></a>
    </section>

    <h2 class="section-heading">Activity</h2>
    <section class="action-grid">
        <a class="action-card" href="${pageContext.request.contextPath}/reports/member-activity"><span class="action-title">Member Activity</span><span class="action-desc">All claims, payments, appeals, EOBs for a member.</span></a>
        <a class="action-card" href="${pageContext.request.contextPath}/reports/provider-activity"><span class="action-title">Provider Activity</span><span class="action-desc">All claims, payments, remittance for a provider.</span></a>
    </section>
</div>
</body>
</html>
