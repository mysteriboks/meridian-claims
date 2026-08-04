<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Trading Partners — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../../fragments/nav.jsp"/>
<div class="content">
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px">
        <h1 style="margin:0">Trading Partners</h1>
        <a class="btn btn-primary" href="${pageContext.request.contextPath}/admin/trading-partners/new">+ New Trading Partner</a>
    </div>
    <p style="color:#6b7888;font-size:13px">
        Each partner's X12 identity and file transport (Phase 13). SFTP credentials are never
        stored here — only a reference key resolved from the external production configuration.
        Active partners are polled by the trading-partner poller job.
    </p>

    <c:if test="${not empty success}"><div class="alert alert-success"><c:out value="${success}"/></div></c:if>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <table class="data-table">
        <thead>
            <tr>
                <th>Partner Name</th>
                <th>ISA ID</th>
                <th>GS ID</th>
                <th>Transport</th>
                <th>Inbound Path</th>
                <th>Status</th>
                <th>Actions</th>
            </tr>
        </thead>
        <tbody>
        <c:forEach var="p" items="${partners}">
            <tr class="${p.active ? '' : 'row-inactive'}">
                <td><a href="${pageContext.request.contextPath}/admin/trading-partners/${p.id}/edit"><c:out value="${p.partnerName}"/></a></td>
                <td><c:out value="${p.isaId}"/></td>
                <td><c:out value="${p.gsId}"/></td>
                <td><c:out value="${p.transportType}"/></td>
                <td style="font-size:12px;color:#6b7888;max-width:220px;word-break:break-word"><c:out value="${p.inboundPath}"/></td>
                <td>
                    <span class="badge ${p.active ? 'badge-active' : 'badge-inactive'}">${p.active ? 'ACTIVE' : 'INACTIVE'}</span>
                </td>
                <td>
                    <a href="${pageContext.request.contextPath}/admin/trading-partners/${p.id}/edit" class="btn-link">Edit</a>
                    <c:if test="${p.active}">
                        <form method="post" action="${pageContext.request.contextPath}/admin/trading-partners/${p.id}/deactivate" style="display:inline">
                            <input type="hidden" name="_csrf" value="${_csrf}">
                            <button type="submit" class="btn-link btn-danger" onclick="return confirm('Deactivate this trading partner? The poller will stop pulling files from it.')">Deactivate</button>
                        </form>
                    </c:if>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty partners}">
            <tr><td colspan="7" style="color:#9aa6b4;text-align:center;padding:24px">No trading partners configured.</td></tr>
        </c:if>
        </tbody>
    </table>

    <div style="margin-top:16px">
        <a href="${pageContext.request.contextPath}/admin/integrations" class="btn">Integrations Monitor</a>
    </div>
</div>
</body>
</html>
