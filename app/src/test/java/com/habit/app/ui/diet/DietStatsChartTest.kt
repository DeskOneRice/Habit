package com.habit.app.ui.diet

import com.habit.app.domain.model.DietDailyTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DietStatsChartTest {
    @Test
    fun sevenDayBucketsKeepRecordsAndMissingCaloriesSeparate() {
        val buckets = chartBuckets(
            listOf(
                DietDailyTotal(0, recordCount = 2, totalCalories = null, calorieRecordCount = 0),
                DietDailyTotal(1, recordCount = 3, totalCalories = 0, calorieRecordCount = 1),
            ),
            DietStatsRange.SEVEN_DAYS,
        )

        assertEquals(2, buckets[0].recordCount)
        assertNull(buckets[0].calories)
        assertEquals(3, buckets[1].recordCount)
        assertEquals(0, buckets[1].calories)
    }

    @Test
    fun thirtyDayBucketsPreservePartialCalorieCoverage() {
        val buckets = chartBuckets(
            listOf(
                DietDailyTotal(0, 1, 120, 1),
                DietDailyTotal(1, 2, null, 0),
            ),
            DietStatsRange.THIRTY_DAYS,
        )

        assertEquals(3, buckets.single().recordCount)
        assertEquals(120, buckets.single().calories)
        assertEquals(1, buckets.single().calorieRecordCount)
    }
}
