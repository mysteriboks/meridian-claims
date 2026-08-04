<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Integrations — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <h1>Integrations</h1>
    <p style="color:#6b7888;font-size:13px">
        EDI transaction log — every inbound 837 and its outbound acknowledgments (999, 277CA,
        TA1). The reconciliation backbone for the interoperability program (Phase 12+).
        Showing most recent <c:out value="${limit}"/> records.
    </p>

    <c:choose>
        <c:when test="${empty transactions}">
            <p style="color:#6b7888">No EDI transactions have been recorded yet.</p>
        </c:when>
        <c:otherwise>
            <table class="data-table">
                <thead>
                    <tr>
                        <th>#</th>
                        <th>Direction</th>
                        <th>Type</th>
                        <th>ISA Control #</th>
                        <th>GS Control #</th>
                        <th>ST Control #</th>
                        <th>Status</th>
                        <th>Related #</th>
                        <th>Partner</th>
                        <th>File</th>
                        <th>Created At</th>
                    </tr>
                </thead>
                <tbody>
                    <c:forEach var="t" items="${transactions}">
                        <tr>
                            <td><c:out value="${t.id}"/></td>
                            <td><c:out value="${t.direction}"/></td>
                            <td><c:out value="${t.transactionType}"/></td>
                            <td><c:out value="${t.isaControlNumber}"/></td>
                            <td><c:out value="${t.gsControlNumber}"/></td>
                            <td><c:out value="${t.stControlNumber}"/></td>
                            <td>
                                <c:choose>
                                    <c:when test="${t.status == 'ACCEPTED'}">
                                        <span class="badge badge-active">ACCEPTED</span>
                                    </c:when>
                                    <c:when test="${t.status == 'PARTIAL'}">
                                        <span class="badge badge-expired">PARTIAL</span>
                                    </c:when>
                                    <c:when test="${t.status == 'REJECTED'}">
                                        <span class="badge badge-inactive">REJECTED</span>
                                    </c:when>
                                    <c:otherwise>
                                        <span class="badge badge-terminated"><c:out value="${t.status}"/></span>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td><c:out value="${t.relatedTransactionId}"/></td>
                            <td>
                                <c:choose>
                                    <c:when test="${not empty t.tradingPartnerId}"><c:out value="${t.tradingPartnerId}"/></c:when>
                                    <c:otherwise><span style="color:#9aa6b4">global</span></c:otherwise>
                                </c:choose>
                            </td>
                            <td style="font-size:12px;color:#6b7888;max-width:220px;word-break:break-word">
                                <c:out value="${t.fileReference}"/>
                            </td>
                            <td>
                                <fmt:formatDate value="${t.createdAt}" pattern="yyyy-MM-dd HH:mm"/>
                            </td>
                        </tr>
                    </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>

    <div style="margin-top:16px">
        <a href="${pageContext.request.contextPath}/admin/intake-batches" class="btn">Intake Batches</a>
        <a href="${pageContext.request.contextPath}/admin/trading-partners" class="btn">Trading Partners</a>
        <a href="${pageContext.request.contextPath}/admin/operations" class="btn">Operations &amp; Job Log</a>
    </div>
</div>
</body>
</html>
