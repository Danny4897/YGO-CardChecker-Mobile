package com.ygochecker.core.designsystem

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Cardmarket (EUR) price formatted for the current locale, e.g. "2,50 €" (it) / "€2.50" (en). */
fun formatPriceEur(value: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
    format.currency = Currency.getInstance("EUR")
    return format.format(value)
}
