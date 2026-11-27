package com.example.mybudget.ui.model

import java.time.LocalDate
import java.time.YearMonth

/** Period shown in the expense history. */
sealed interface DateFilter {
    /** First and last day included, or null for no limit. */
    fun range(today: LocalDate): ClosedRange<LocalDate>?

    data object AllTime : DateFilter {
        override fun range(today: LocalDate): ClosedRange<LocalDate>? = null
    }

    data object ThisMonth : DateFilter {
        override fun range(today: LocalDate) = YearMonth.from(today).let { it.atDay(1)..it.atEndOfMonth() }
    }

    data object LastMonth : DateFilter {
        override fun range(today: LocalDate) = YearMonth.from(today).minusMonths(1).let { it.atDay(1)..it.atEndOfMonth() }
    }

    data class Custom(val start: LocalDate, val end: LocalDate) : DateFilter {
        override fun range(today: LocalDate) = start..end
    }

    companion object {
        /** For rememberSaveable: "all", "this", "last" or "custom:<start>:<end>" (ISO dates). */
        fun encode(filter: DateFilter): String = when (filter) {
            AllTime -> "all"
            ThisMonth -> "this"
            LastMonth -> "last"
            is Custom -> "custom:${filter.start}:${filter.end}"
        }

        fun decode(value: String): DateFilter = when {
            value == "this" -> ThisMonth
            value == "last" -> LastMonth
            value.startsWith("custom:") -> value.split(':').let { Custom(LocalDate.parse(it[1]), LocalDate.parse(it[2])) }
            else -> AllTime
        }
    }
}

fun List<Expense>.inPeriod(filter: DateFilter, today: LocalDate = LocalDate.now()): List<Expense> {
    val range = filter.range(today) ?: return this
    return filter { it.date in range }
}
