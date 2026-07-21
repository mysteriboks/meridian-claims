<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>New Claim — Meridian Claims</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/main.css">
    <style>
        .row-group { display:flex; gap:12px; flex-wrap:wrap; margin-bottom:10px; align-items:flex-end; }
        .row-group .form-group { margin:0; }
        .remove-btn { margin-bottom:6px; }
        .line-items-table, .diag-table { width:100%; border-collapse:collapse; margin-bottom:8px; }
        .line-items-table th, .diag-table th { background:#f5f7fa; padding:7px 10px; text-align:left; font-size:13px; font-weight:600; border-bottom:1px solid #dde3ec; }
        .line-items-table td, .diag-table td { padding:6px 8px; border-bottom:1px solid #eef1f5; vertical-align:middle; }
        .line-items-table input[type=text], .diag-table input[type=text] { width:100%; box-sizing:border-box; }
        /* Searchable code picker */
        .code-picker { position:relative; display:inline-block; width:360px; }
        .code-picker .picker-filter {
            width:100%; box-sizing:border-box; padding:5px 8px;
            border:1px solid #c8d0db; border-radius:4px 4px 0 0; font-size:13px;
        }
        .code-picker select {
            width:100%; box-sizing:border-box; border:1px solid #c8d0db; border-top:none;
            border-radius:0 0 4px 4px; font-size:13px; background:#fff;
        }
        .code-picker .picker-empty { font-size:12px; color:#9aa6b4; padding:4px 8px; }
    </style>
</head>
<body>
<jsp:include page="../fragments/nav.jsp"/>
<div class="content">
    <h1>Submit Claim</h1>

    <c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>

    <c:if test="${empty procedureCodes}">
        <div class="alert alert-error">
            No procedure codes are configured. Please ask an administrator to add procedure codes in
            <a href="${pageContext.request.contextPath}/admin/lookups/procedure-codes">Admin → Procedure Codes</a>
            before submitting claims.
        </div>
    </c:if>
    <c:if test="${empty diagnosisCodes}">
        <div class="alert alert-error">
            No diagnosis codes are configured. Please ask an administrator to add diagnosis codes in
            <a href="${pageContext.request.contextPath}/admin/lookups/diagnosis-codes">Admin → Diagnosis Codes</a>
            before submitting claims.
        </div>
    </c:if>

    <form method="post" action="${pageContext.request.contextPath}/claims">
        <input type="hidden" name="_csrf" value="${_csrf}">

        <h2 class="section-heading">Claim Header</h2>
        <div style="display:flex;gap:16px;flex-wrap:wrap">
            <div class="form-group">
                <label>Claim Type *</label>
                <select name="claimType">
                    <c:forEach var="ct" items="${claimTypes}">
                        <option value="${ct}" ${claimRequest.claimType == ct ? 'selected' : ''}><c:out value="${ct}"/></option>
                    </c:forEach>
                </select>
            </div>
            <div class="form-group">
                <label>Original Claim ID <span style="color:#9aa6b4;font-size:12px">(CORRECTED / VOID only)</span></label>
                <input type="number" name="originalClaimId" value="<c:out value='${claimRequest.originalClaimId}'/>" style="width:140px">
            </div>
        </div>

        <div style="display:flex;gap:16px;flex-wrap:wrap;margin-top:4px">
            <div class="form-group">
                <label>Member *</label>
                <select name="memberId" required style="min-width:220px">
                    <option value="">— select member —</option>
                    <c:forEach var="m" items="${members}">
                        <option value="${m.id}" ${claimRequest.memberId == m.id ? 'selected' : ''}>
                            <c:out value="${m.memberNumber}"/> — <c:out value="${m.fullName}"/>
                        </option>
                    </c:forEach>
                </select>
            </div>
            <div class="form-group">
                <label>Provider *</label>
                <select name="providerId" required style="min-width:220px">
                    <option value="">— select provider —</option>
                    <c:forEach var="p" items="${providers}">
                        <option value="${p.id}" ${claimRequest.providerId == p.id ? 'selected' : ''}>
                            <c:out value="${p.name}"/> (<c:out value="${p.npi}"/>)
                        </option>
                    </c:forEach>
                </select>
            </div>
            <div class="form-group">
                <label>Date of Service *</label>
                <input type="date" name="dateOfService" required
                       value="<fmt:formatDate value='${claimRequest.dateOfService}' pattern='yyyy-MM-dd'/>">
            </div>
            <div class="form-group">
                <label>Coverage Order *</label>
                <select name="coverageOrder" id="coverageOrder" onchange="toggleCob()" style="width:130px">
                    <option value="PRIMARY"   ${claimRequest.coverageOrder == 'PRIMARY'   ? 'selected' : ''}>PRIMARY</option>
                    <option value="SECONDARY" ${claimRequest.coverageOrder == 'SECONDARY' ? 'selected' : ''}>SECONDARY</option>
                </select>
            </div>
            <div class="form-group" id="cobSection" style="display:none">
                <label>Primary Paid Amount (COB) *</label>
                <input type="text" name="cobPrimaryPaid" value="<c:out value='${claimRequest.cobPrimaryPaid}'/>"
                       style="width:120px" placeholder="0.00">
            </div>
        </div>

        <div style="display:flex;gap:16px;flex-wrap:wrap;margin-top:4px">
            <div class="form-group">
                <label>Prior Auth Number</label>
                <input type="text" name="priorAuthNumber" value="<c:out value='${claimRequest.priorAuthNumber}'/>" style="width:160px">
            </div>
            <div class="form-group">
                <label>Referral Number</label>
                <input type="text" name="referralNumber" value="<c:out value='${claimRequest.referralNumber}'/>" style="width:160px">
            </div>
            <div class="form-group" style="padding-top:22px">
                <%-- Hidden field ensures false is submitted when checkbox is unchecked --%>
                <input type="hidden" name="accidentIndicator" value="false"/>
                <label><input type="checkbox" name="accidentIndicator" value="true"
                       ${claimRequest.accidentIndicator ? 'checked' : ''}
                       onchange="toggleAccident()"> Accident-related</label>
            </div>
        </div>
        <div id="accidentSection" style="display:none;display:flex;gap:16px;flex-wrap:wrap;margin-top:4px">
            <div class="form-group">
                <label>Accident Type</label>
                <select name="accidentType" style="width:160px">
                    <option value="">—</option>
                    <option value="AUTO"     ${claimRequest.accidentType == 'AUTO'     ? 'selected' : ''}>Auto</option>
                    <option value="WORK"     ${claimRequest.accidentType == 'WORK'     ? 'selected' : ''}>Work</option>
                    <option value="OTHER"    ${claimRequest.accidentType == 'OTHER'    ? 'selected' : ''}>Other</option>
                </select>
            </div>
            <div class="form-group">
                <label>Accident Date</label>
                <input type="date" name="accidentDate"
                       value="<fmt:formatDate value='${claimRequest.accidentDate}' pattern='yyyy-MM-dd'/>">
            </div>
        </div>

        <div class="form-group" style="margin-top:8px">
            <label>Notes</label>
            <textarea name="notes" style="width:100%;max-width:640px;height:56px"><c:out value="${claimRequest.notes}"/></textarea>
        </div>

        <%-- ================================================================
             DIAGNOSES — dropdown from diagnosis_codes master table only
             ================================================================ --%>
        <h2 class="section-heading" style="margin-top:24px">
            Diagnoses <span style="font-size:13px;font-weight:400;color:#9aa6b4">(1 PRIMARY required · max 12)</span>
        </h2>
        <table class="diag-table" id="diagTable">
            <thead><tr><th style="width:110px">Type</th><th>Diagnosis Code</th><th style="width:48px"></th></tr></thead>
            <tbody id="diagBody">
            <c:choose>
                <c:when test="${not empty claimRequest.diagnoses}">
                    <c:forEach var="d" items="${claimRequest.diagnoses}" varStatus="i">
                        <tr>
                            <td>
                                <select name="diagnoses[${i.index}].diagnosisType" style="width:105px">
                                    <option value="PRIMARY"   ${d.diagnosisType == 'PRIMARY'   ? 'selected' : ''}>PRIMARY</option>
                                    <option value="SECONDARY" ${d.diagnosisType == 'SECONDARY' ? 'selected' : ''}>SECONDARY</option>
                                </select>
                            </td>
                            <td><div class="code-picker">
                                <input type="text" class="picker-filter" placeholder="Type to search…" oninput="filterPicker(this)">
                                <select name="diagnoses[${i.index}].diagnosisCode" required size="5">
                                    <option value="">— select code —</option>
                                    <c:forEach var="dx" items="${diagnosisCodes}">
                                        <option value="<c:out value='${dx.code}'/>" ${d.diagnosisCode == dx.code ? 'selected' : ''}>
                                            <c:out value="${dx.code}"/> — <c:out value="${dx.description}"/>
                                        </option>
                                    </c:forEach>
                                </select>
                            </div></td>
                            <td><button type="button" class="btn-link btn-danger remove-btn" onclick="removeDiagRow(this)">✕</button></td>
                        </tr>
                    </c:forEach>
                </c:when>
                <c:otherwise>
                    <tr>
                        <td><select name="diagnoses[0].diagnosisType" style="width:105px">
                            <option value="PRIMARY" selected>PRIMARY</option>
                            <option value="SECONDARY">SECONDARY</option>
                        </select></td>
                        <td><div class="code-picker">
                            <input type="text" class="picker-filter" placeholder="Type to search…" oninput="filterPicker(this)">
                            <select name="diagnoses[0].diagnosisCode" required size="5">
                                <option value="">— select code —</option>
                                <c:forEach var="dx" items="${diagnosisCodes}">
                                    <option value="<c:out value='${dx.code}'/>">
                                        <c:out value="${dx.code}"/> — <c:out value="${dx.description}"/>
                                    </option>
                                </c:forEach>
                            </select>
                        </div></td>
                        <td><button type="button" class="btn-link btn-danger remove-btn" onclick="removeDiagRow(this)">✕</button></td>
                    </tr>
                </c:otherwise>
            </c:choose>
            </tbody>
        </table>
        <button type="button" class="btn" onclick="addDiagRow()" style="margin-bottom:20px">+ Add Diagnosis</button>

        <%-- ================================================================
             LINE ITEMS — dropdown from procedure_codes master table only
             ================================================================ --%>
        <h2 class="section-heading" style="margin-top:8px">Line Items</h2>
        <table class="line-items-table" id="liTable">
            <thead><tr><th>Procedure Code</th><th style="width:120px">Billed Amount ($)</th><th style="width:48px"></th></tr></thead>
            <tbody id="liBody">
            <c:choose>
                <c:when test="${not empty claimRequest.lineItems}">
                    <c:forEach var="li" items="${claimRequest.lineItems}" varStatus="i">
                        <tr>
                            <td><div class="code-picker">
                                <input type="text" class="picker-filter" placeholder="Type to search…" oninput="filterPicker(this)">
                                <select name="lineItems[${i.index}].procedureCode" required size="5">
                                    <option value="">— select procedure —</option>
                                    <c:forEach var="pc" items="${procedureCodes}">
                                        <option value="<c:out value='${pc.code}'/>" ${li.procedureCode == pc.code ? 'selected' : ''}>
                                            <c:out value="${pc.code}"/> — <c:out value="${pc.description}"/>
                                            <c:if test="${not empty pc.serviceType}"> (<c:out value="${pc.serviceType}"/>)</c:if>
                                        </option>
                                    </c:forEach>
                                </select>
                            </div></td>
                            <td><input type="text" name="lineItems[${i.index}].billedAmount"
                                       value="<c:out value='${li.billedAmount}'/>" placeholder="0.00" style="width:110px"></td>
                            <td><button type="button" class="btn-link btn-danger remove-btn" onclick="removeLiRow(this)">✕</button></td>
                        </tr>
                    </c:forEach>
                </c:when>
                <c:otherwise>
                    <tr>
                        <td><div class="code-picker">
                            <input type="text" class="picker-filter" placeholder="Type to search…" oninput="filterPicker(this)">
                            <select name="lineItems[0].procedureCode" required size="5">
                                <option value="">— select procedure —</option>
                                <c:forEach var="pc" items="${procedureCodes}">
                                    <option value="<c:out value='${pc.code}'/>">
                                        <c:out value="${pc.code}"/> — <c:out value="${pc.description}"/>
                                        <c:if test="${not empty pc.serviceType}"> (<c:out value="${pc.serviceType}"/>)</c:if>
                                    </option>
                                </c:forEach>
                            </select>
                        </div></td>
                        <td><input type="text" name="lineItems[0].billedAmount" placeholder="0.00" style="width:110px"></td>
                        <td><button type="button" class="btn-link btn-danger remove-btn" onclick="removeLiRow(this)">✕</button></td>
                    </tr>
                </c:otherwise>
            </c:choose>
            </tbody>
        </table>
        <button type="button" class="btn" onclick="addLiRow()" style="margin-bottom:28px">+ Add Line Item</button>

        <div>
            <button type="submit" class="btn btn-primary">Submit Claim</button>
            <a href="${pageContext.request.contextPath}/claims" class="btn" style="margin-left:8px">Cancel</a>
        </div>
    </form>
</div>

<script>
// ---- COB toggle ----
function toggleCob() {
    var val = document.getElementById('coverageOrder').value;
    document.getElementById('cobSection').style.display = (val === 'SECONDARY') ? '' : 'none';
}
toggleCob();

// ---- Accident toggle ----
function toggleAccident() {
    var cb = document.querySelector('input[name="accidentIndicator"][type="checkbox"]');
    document.getElementById('accidentSection').style.display = cb.checked ? 'flex' : 'none';
}
// Restore accident section if re-rendered with error
(function() {
    var cb = document.querySelector('input[name="accidentIndicator"][type="checkbox"]');
    if (cb && cb.checked) { document.getElementById('accidentSection').style.display = 'flex'; }
})();

// ---- Serialised diagnosis options (JSON for dynamic rows) ----
var DIAG_OPTIONS = [
    <c:forEach var="dx" items="${diagnosisCodes}" varStatus="s">
    { code: '<c:out value="${dx.code}"/>', desc: '<c:out value="${dx.description}"/>' }<c:if test="${!s.last}">,</c:if>
    </c:forEach>
];

// ---- Serialised procedure options ----
var PROC_OPTIONS = [
    <c:forEach var="pc" items="${procedureCodes}" varStatus="s">
    { code: '<c:out value="${pc.code}"/>', desc: '<c:out value="${pc.description}"/>', svc: '<c:out value="${pc.serviceType}"/>' }<c:if test="${!s.last}">,</c:if>
    </c:forEach>
];

// Filters a .code-picker select to options matching the typed text
function filterPicker(input) {
    var q = input.value.toLowerCase();
    var sel = input.nextElementSibling;
    var opts = sel.options;
    for (var i = 0; i < opts.length; i++) {
        var text = opts[i].textContent.toLowerCase();
        opts[i].hidden = (q.length > 0 && text.indexOf(q) === -1);
    }
    // Auto-select first visible match when only one remains
    var visible = [];
    for (var i = 1; i < opts.length; i++) {
        if (!opts[i].hidden) visible.push(i);
    }
    if (visible.length === 1) { sel.selectedIndex = visible[0]; }
}

function buildPicker(name, options, labelFn) {
    var div = document.createElement('div');
    div.className = 'code-picker';

    var filter = document.createElement('input');
    filter.type = 'text';
    filter.className = 'picker-filter';
    filter.placeholder = 'Type to search…';
    filter.oninput = function() { filterPicker(filter); };
    div.appendChild(filter);

    var sel = document.createElement('select');
    sel.name = name;
    sel.required = true;
    sel.size = 5;
    var blank = document.createElement('option');
    blank.value = '';
    blank.textContent = '— select —';
    sel.appendChild(blank);
    for (var i = 0; i < options.length; i++) {
        var opt = document.createElement('option');
        opt.value = options[i].code;
        opt.textContent = labelFn(options[i]);
        sel.appendChild(opt);
    }
    div.appendChild(sel);
    return div;
}

var diagIdx = document.querySelectorAll('#diagBody tr').length;
var liIdx   = document.querySelectorAll('#liBody tr').length;

function addDiagRow() {
    if (diagIdx >= 12) { alert('Maximum 12 diagnoses per claim.'); return; }
    var tr = document.createElement('tr');

    var tdType = document.createElement('td');
    var sel = document.createElement('select');
    sel.name = 'diagnoses[' + diagIdx + '].diagnosisType';
    sel.style.width = '105px';
    ['PRIMARY','SECONDARY'].forEach(function(v) {
        var o = document.createElement('option');
        o.value = v; o.textContent = v;
        if (v === 'SECONDARY') o.selected = true;
        sel.appendChild(o);
    });
    tdType.appendChild(sel);

    var tdCode = document.createElement('td');
    tdCode.appendChild(buildPicker('diagnoses[' + diagIdx + '].diagnosisCode', DIAG_OPTIONS,
        function(d) { return d.code + ' — ' + d.desc; }));

    var tdBtn = document.createElement('td');
    var btn = document.createElement('button');
    btn.type = 'button'; btn.className = 'btn-link btn-danger remove-btn';
    btn.textContent = '✕'; btn.onclick = function() { removeDiagRow(btn); };
    tdBtn.appendChild(btn);

    tr.appendChild(tdType); tr.appendChild(tdCode); tr.appendChild(tdBtn);
    document.getElementById('diagBody').appendChild(tr);
    diagIdx++;
}

function addLiRow() {
    var tr = document.createElement('tr');

    var tdProc = document.createElement('td');
    tdProc.appendChild(buildPicker('lineItems[' + liIdx + '].procedureCode', PROC_OPTIONS,
        function(p) { return p.code + ' — ' + p.desc + (p.svc ? ' (' + p.svc + ')' : ''); }));

    var tdAmt = document.createElement('td');
    var inp = document.createElement('input');
    inp.type = 'text'; inp.name = 'lineItems[' + liIdx + '].billedAmount';
    inp.placeholder = '0.00'; inp.style.width = '110px';
    tdAmt.appendChild(inp);

    var tdBtn = document.createElement('td');
    var btn = document.createElement('button');
    btn.type = 'button'; btn.className = 'btn-link btn-danger remove-btn';
    btn.textContent = '✕'; btn.onclick = function() { removeLiRow(btn); };
    tdBtn.appendChild(btn);

    tr.appendChild(tdProc); tr.appendChild(tdAmt); tr.appendChild(tdBtn);
    document.getElementById('liBody').appendChild(tr);
    liIdx++;
}

function removeDiagRow(btn) {
    var tbody = document.getElementById('diagBody');
    if (tbody.rows.length <= 1) { alert('At least one diagnosis is required.'); return; }
    btn.closest('tr').remove();
}

function removeLiRow(btn) {
    var tbody = document.getElementById('liBody');
    if (tbody.rows.length <= 1) { alert('At least one line item is required.'); return; }
    btn.closest('tr').remove();
}
</script>
</body>
</html>
