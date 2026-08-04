package com.meridian.claims.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Builds a NACHA-format ACH credit file (CCD+ — one batch, one addenda record
 * per entry carrying the TRN reassociation number) from a flat list of
 * entries. Fixed-width, 94 characters per record, CR/LF-terminated, blocked
 * in groups of 10 with "9"-filler records — the standard NACHA file
 * structure. Plain `util/` class (no Spring dependency), mirroring
 * {@link CsvWriter}'s role for CSV.
 *
 * Structural simplification, consistent with every EDI generator in this
 * codebase: real NACHA field positions and control totals (entry hash,
 * debit/credit totals, batch/block/entry counts), but no live ACH-operator
 * submission — this produces a file an ODFI would need to actually transmit.
 */
public final class AchFileWriter {

    private static final String RECORD_TERMINATOR = "\r\n";
    private static final int RECORDS_PER_BLOCK = 10;

    private AchFileWriter() {
        // static helpers only
    }

    /** One ACH credit entry — one per payee (provider) receiving a disbursement. */
    public static final class Entry {
        private final String routingNumber;
        private final String accountNumber;
        private final String accountType; // CHECKING / SAVINGS
        private final BigDecimal amount;
        private final String individualId;
        private final String individualName;

        public Entry(String routingNumber, String accountNumber, String accountType,
                     BigDecimal amount, String individualId, String individualName) {
            this.routingNumber = routingNumber;
            this.accountNumber = accountNumber;
            this.accountType = accountType;
            this.amount = amount;
            this.individualId = individualId;
            this.individualName = individualName;
        }

        public String getRoutingNumber() { return routingNumber; }
        public String getAccountNumber() { return accountNumber; }
        public String getAccountType() { return accountType; }
        public BigDecimal getAmount() { return amount; }
        public String getIndividualId() { return individualId; }
        public String getIndividualName() { return individualName; }
    }

    /**
     * Generates the full NACHA file text for one batch of credit entries.
     *
     * @param originRoutingNumber the originating (Meridian's) ODFI routing number, 9 digits
     * @param originName          the originating institution/company display name
     * @param companyId           10-character company identification (batch header + control)
     * @param companyName         company name for the batch header (payer)
     * @param effectiveDate       the date entries should post
     * @param trnReassociationNumber carried on each entry's addenda record, matching the paired 835's TRN02
     * @param entries             one per payee; must not be empty
     */
    public static String generate(String originRoutingNumber, String originName, String companyId,
                                   String companyName, Date effectiveDate, String trnReassociationNumber,
                                   List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("At least one ACH entry is required");
        }

        StringBuilder sb = new StringBuilder();
        Date now = new Date();
        String originRouting8 = originRoutingNumber.substring(0, 8);
        int recordCount = 0;

        // ----- File Header (Type 1) -----
        sb.append('1')
          .append("01")
          .append(pad(" " + originRoutingNumber, 10))
          .append(pad(" " + originRoutingNumber, 10))
          .append(fmt(now, "yyMMdd"))
          .append(fmt(now, "HHmm"))
          .append('A')
          .append("094")
          .append("10")
          .append('1')
          .append(padRight(originName, 23))
          .append(padRight(originName, 23))
          .append(padRight("", 8))
          .append(RECORD_TERMINATOR);
        recordCount++;

        // ----- Batch Header (Type 5) -----
        sb.append('5')
          .append("220")
          .append(padRight(companyName, 16))
          .append(padRight("", 20))
          .append(padRight(companyId, 10))
          .append("CCD")
          .append(padRight("CLAIMPMT", 10))
          .append(padRight("", 6))
          .append(fmt(effectiveDate, "yyMMdd"))
          .append(padRight("", 3))
          .append('1')
          .append(originRouting8)
          .append(zeroPad("1", 7))
          .append(RECORD_TERMINATOR);
        recordCount++;

        long entryHashTotal = 0;
        BigDecimal totalCredits = BigDecimal.ZERO;
        int entrySeq = 0;

        for (Entry entry : entries) {
            entrySeq++;
            String routing8 = entry.routingNumber.substring(0, 8);
            String checkDigit = entry.routingNumber.substring(8, 9);
            entryHashTotal += Long.parseLong(routing8);
            totalCredits = totalCredits.add(entry.amount);
            String transactionCode = "SAVINGS".equals(entry.accountType) ? "32" : "22";
            String traceNumber = originRouting8 + zeroPad(String.valueOf(entrySeq), 7);

            // ----- Entry Detail (Type 6) -----
            sb.append('6')
              .append(transactionCode)
              .append(routing8)
              .append(checkDigit)
              .append(padRight(entry.accountNumber, 17))
              .append(zeroPad(toCents(entry.amount), 10))
              .append(padRight(nz(entry.individualId), 15))
              .append(padRight(nz(entry.individualName), 22))
              .append(padRight("", 2))
              .append('1')
              .append(traceNumber)
              .append(RECORD_TERMINATOR);
            recordCount++;

            // ----- Addenda (Type 7, CCD+ 05) — carries the TRN reassociation number -----
            String paymentRelatedInfo = "TRN*1*" + trnReassociationNumber;
            sb.append('7')
              .append("05")
              .append(padRight(paymentRelatedInfo, 80))
              .append(zeroPad("1", 4))
              .append(zeroPad(String.valueOf(entrySeq), 7))
              .append(RECORD_TERMINATOR);
            recordCount++;
        }

        int entryAddendaCount = entrySeq * 2;
        String entryHash10 = zeroPad(String.valueOf(entryHashTotal % 10_000_000_000L), 10);
        String totalCreditsStr = zeroPad(toCents(totalCredits), 12);
        String totalDebitsStr = zeroPad("0", 12);

        // ----- Batch Control (Type 8) -----
        sb.append('8')
          .append("220")
          .append(zeroPad(String.valueOf(entryAddendaCount), 6))
          .append(entryHash10)
          .append(totalDebitsStr)
          .append(totalCreditsStr)
          .append(padRight(companyId, 10))
          .append(padRight("", 19))
          .append(padRight("", 6))
          .append(originRouting8)
          .append(zeroPad("1", 7))
          .append(RECORD_TERMINATOR);
        recordCount++;

        // ----- File Control (Type 9) -----
        int recordCountBeforeFileControl = recordCount + 1; // +1 for the file control record itself
        int blockCount = (int) Math.ceil(recordCountBeforeFileControl / (double) RECORDS_PER_BLOCK);
        sb.append('9')
          .append(zeroPad("1", 6))
          .append(zeroPad(String.valueOf(blockCount), 6))
          .append(zeroPad(String.valueOf(entryAddendaCount), 8))
          .append(entryHash10)
          .append(totalDebitsStr)
          .append(totalCreditsStr)
          .append(padRight("", 39))
          .append(RECORD_TERMINATOR);
        recordCount++;

        // ----- Filler records to complete the final block of 10 -----
        int remainder = recordCount % RECORDS_PER_BLOCK;
        if (remainder != 0) {
            int fillers = RECORDS_PER_BLOCK - remainder;
            for (int i = 0; i < fillers; i++) {
                sb.append(padRight("", 94, '9')).append(RECORD_TERMINATOR);
            }
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String toCents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .toBigInteger()
            .toString();
    }

    private static String fmt(Date date, String pattern) {
        return new SimpleDateFormat(pattern).format(date);
    }

    private static String nz(String value) {
        return value != null ? value : "";
    }

    /** Left-pads with spaces to exactly {@code width}, truncating if longer. */
    private static String pad(String value, int width) {
        String v = value == null ? "" : value;
        if (v.length() > width) return v.substring(0, width);
        StringBuilder sb = new StringBuilder();
        for (int i = v.length(); i < width; i++) sb.append(' ');
        sb.append(v);
        return sb.toString();
    }

    /** Right-pads with spaces to exactly {@code width}, truncating if longer. */
    private static String padRight(String value, int width) {
        return padRight(value, width, ' ');
    }

    private static String padRight(String value, int width, char padChar) {
        String v = value == null ? "" : value;
        if (v.length() > width) return v.substring(0, width);
        StringBuilder sb = new StringBuilder(v);
        while (sb.length() < width) sb.append(padChar);
        return sb.toString();
    }

    /** Zero-pads a numeric string on the left to exactly {@code width}. */
    private static String zeroPad(String value, int width) {
        String v = value == null ? "0" : value;
        if (v.length() > width) return v.substring(v.length() - width);
        StringBuilder sb = new StringBuilder();
        for (int i = v.length(); i < width; i++) sb.append('0');
        sb.append(v);
        return sb.toString();
    }
}
