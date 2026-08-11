package com.habit.app.domain.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalorieEstimatePromptTest {
    @Test
    fun mealPromptCarriesStructuredRecordAlongsideSelectedPhotos() {
        val prompt = CalorieEstimatePrompt.userPrompt(
            CalorieEstimateRecordInput(
                recordType = "MEAL",
                category = "家常菜",
                mealType = "LUNCH",
                description = "牛肉饭配时蔬",
                foodItems = listOf(CalorieEstimateFoodInput("米饭", "180 克", 210)),
                beverage = null,
                note = "少油",
                selectedPhotoCount = 2,
            ),
        )

        assertTrue(prompt.contains("\"recordType\":\"MEAL\""))
        assertTrue(prompt.contains("\"category\":\"家常菜\""))
        assertTrue(prompt.contains("\"mealType\":\"LUNCH\""))
        assertTrue(prompt.contains("\"name\":\"米饭\""))
        assertTrue(prompt.contains("\"portion\":\"180 克\""))
        assertTrue(prompt.contains("\"recordedKcal\":210"))
        assertTrue(prompt.contains("\"note\":\"少油\""))
        assertTrue(prompt.contains("\"selectedPhotoCount\":2"))
        assertFalse(prompt.contains("createdAt"))
        assertFalse(prompt.contains("relativePath"))
    }

    @Test
    fun beveragePromptCarriesOrderAttributesAndToppings() {
        val prompt = CalorieEstimatePrompt.userPrompt(
            CalorieEstimateRecordInput(
                recordType = "BEVERAGE",
                category = "奶茶",
                mealType = null,
                description = "",
                foodItems = emptyList(),
                beverage = CalorieEstimateBeverageInput(
                    brandOrStore = "茶铺",
                    name = "芋泥奶茶",
                    sizeOrVolume = "大杯",
                    temperature = "正常冰",
                    sweetness = "三分糖",
                    toppings = listOf("珍珠", "芋圆"),
                    cupCount = 2,
                ),
                note = "去奶盖",
                selectedPhotoCount = 1,
            ),
        )

        listOf("BEVERAGE", "奶茶", "茶铺", "芋泥奶茶", "大杯", "正常冰", "三分糖", "珍珠", "芋圆", "去奶盖")
            .forEach { assertTrue("missing $it", prompt.contains(it)) }
        assertTrue(prompt.contains("\"cupCount\":2"))
        assertTrue(prompt.contains("\"mealType\":null"))
    }
}
