package com.meridian.claims.intake.fhir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** FHIR R4 Money data type — value + currency. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FhirMoney {

    private BigDecimal value;
    private String currency;

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
