<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${empty partner ? 'New Trading Partner' : 'Edit Trading Partner'} — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
</head>
<body>
<jsp:include page="../../fragments/nav.jsp"/>
<div class="content" style="max-width:680px">
    <h1>${empty partner ? 'New Trading Partner' : 'Edit Trading Partner'}</h1>
    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <c:choose>
        <c:when test="${empty partner}">
            <form method="post" action="${pageContext.request.contextPath}/admin/trading-partners/new">
        <input type="hidden" name="_csrf" value="${_csrf}">
        </c:when>
        <c:otherwise>
            <form method="post" action="${pageContext.request.contextPath}/admin/trading-partners/${partner.id}/edit">
        <input type="hidden" name="_csrf" value="${_csrf}">
        </c:otherwise>
    </c:choose>

    <div class="form-group">
        <label for="partnerName">Partner Name *</label>
        <input type="text" id="partnerName" name="partnerName" required maxlength="100"
               value="<c:out value='${partner.partnerName}'/>">
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="isaQualifier">ISA Qualifier *</label>
            <input type="text" id="isaQualifier" name="isaQualifier" required maxlength="2"
                   value="<c:out value='${partner.isaQualifier}'/>" placeholder="ZZ">
        </div>
        <div class="form-group">
            <label for="isaId">ISA ID *</label>
            <input type="text" id="isaId" name="isaId" required maxlength="15"
                   value="<c:out value='${partner.isaId}'/>">
        </div>
        <div class="form-group">
            <label for="gsId">GS ID *</label>
            <input type="text" id="gsId" name="gsId" required maxlength="15"
                   value="<c:out value='${partner.gsId}'/>">
        </div>
    </div>

    <div class="form-group">
        <label for="enabledTransactions">Enabled Transactions</label>
        <input type="text" id="enabledTransactions" name="enabledTransactions" maxlength="200"
               value="<c:out value='${partner.enabledTransactions}'/>" placeholder="837,999,277CA,835">
    </div>

    <div style="display:grid;grid-template-columns:1fr 2fr 1fr;gap:16px">
        <div class="form-group">
            <label for="transportType">Transport *</label>
            <select id="transportType" name="transportType">
                <option value="LOCAL" ${empty partner.transportType || partner.transportType == 'LOCAL' ? 'selected' : ''}>LOCAL</option>
                <option value="SFTP" ${partner.transportType == 'SFTP' ? 'selected' : ''}>SFTP</option>
            </select>
        </div>
        <div class="form-group">
            <label for="transportHost">SFTP Host</label>
            <input type="text" id="transportHost" name="transportHost" maxlength="255"
                   value="<c:out value='${partner.transportHost}'/>" placeholder="sftp.partner.example.com">
        </div>
        <div class="form-group">
            <label for="transportPort">SFTP Port</label>
            <input type="number" id="transportPort" name="transportPort" min="1" max="65535"
                   value="<c:out value='${partner.transportPort}'/>" placeholder="22">
        </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="transportUsername">SFTP Username</label>
            <input type="text" id="transportUsername" name="transportUsername" maxlength="100"
                   value="<c:out value='${partner.transportUsername}'/>">
        </div>
        <div class="form-group">
            <label for="transportCredentialRef">Credential Reference</label>
            <input type="text" id="transportCredentialRef" name="transportCredentialRef" maxlength="200"
                   value="<c:out value='${partner.transportCredentialRef}'/>" placeholder="e.g. acme-sftp">
            <div style="font-size:12px;color:#9aa6b4;margin-top:4px">
                Resolves to <code>claims.transport.credential.&lt;ref&gt;</code> in the production
                configuration overlay — the password itself is never entered here.
            </div>
        </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
        <div class="form-group">
            <label for="inboundPath">Inbound Path *</label>
            <input type="text" id="inboundPath" name="inboundPath" required maxlength="500"
                   value="<c:out value='${partner.inboundPath}'/>"
                   placeholder="Local dir (LOCAL) or remote dir (SFTP)">
        </div>
        <div class="form-group">
            <label for="outboundPath">Outbound Path</label>
            <input type="text" id="outboundPath" name="outboundPath" maxlength="500"
                   value="<c:out value='${partner.outboundPath}'/>">
        </div>
    </div>

    <div class="form-group">
        <label>
            <input type="checkbox" name="active" value="true" ${empty partner || partner.active ? 'checked' : ''}>
            Active (polled by the trading-partner poller job)
        </label>
    </div>

    <div style="display:flex;gap:10px;margin-top:8px">
        <button type="submit" class="btn btn-primary">${empty partner ? 'Create Partner' : 'Save Changes'}</button>
        <a href="${pageContext.request.contextPath}/admin/trading-partners" class="btn">Cancel</a>
    </div>
    </form>
</div>
</body>
</html>
