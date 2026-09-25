package com.chequetracker.watch.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun indianGrouping() {
        assertEquals("₹0", formatInr(0.0))
        assertEquals("₹999", formatInr(999.0))
        assertEquals("₹1,000", formatInr(1000.0))
        assertEquals("₹1,25,000", formatInr(125000.0))
        assertEquals("₹12,34,567", formatInr(1234567.0))
        assertEquals("₹1,00,00,000", formatInr(10000000.0))
        assertEquals("₹45,000.50", formatInr(45000.5))
        assertEquals("₹1,79,800.50", formatInr(179800.5))
        assertEquals("₹0.01", formatInr(0.01))
    }

    @Test fun countLabel() {
        assertEquals("1 cheque", chequeCountLabel(1))
        assertEquals("5 cheques", chequeCountLabel(5))
        assertEquals("0 cheques", chequeCountLabel(0))
    }

    @Test fun overdue() {
        assertEquals("+2 overdue · ₹30,000.25", overdueLabel(2, 30000.25))
    }

    @Test fun decodesServerJson() {
        val json = """{"date":"2026-09-25","amount_needed":134800.0,"count":1,"overdue_count":2,
            "overdue_amount_needed":20000.0,"updated_at":"2026-09-25T07:05:39.975Z","cheques":[{"id":"x",
            "party_name":"Gupta & Sons","amount":9800,"status":"PENDING","cheque_number":"100236",
            "bank_name":"HDFC","due_date":"2026-09-25","original_due_date":"2026-09-15",
            "represent_count":2,"return_reason":"Insufficient funds","future_field":1}]}"""
        val d = AppJson.decodeFromString(TodayData.serializer(), json)
        assertEquals(2, d.cheques.single().representCount)
        assertEquals("2026-09-15", d.cheques.single().originalDueDate)
        assertEquals(134800.0, d.amountNeeded, 0.0)
        assertEquals(20000.0, d.overdueAmountNeeded, 0.0)
    }
}
