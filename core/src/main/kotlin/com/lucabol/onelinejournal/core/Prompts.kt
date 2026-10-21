package com.lucabol.onelinejournal.core

import java.time.LocalDate

object Prompts {
    private val questions = listOf(
        "What small moment would you like to remember?",
        "What made today different from yesterday?",
        "Who made your day a little better?",
        "What surprised you today?",
        "What did you notice that you usually overlook?",
        "What would you tell your future self about today?",
        "What made you smile?",
        "What felt difficult, and what helped?",
        "What did you learn without expecting to?",
        "What did you make time for today?",
        "What ordinary thing felt special?",
        "What conversation stayed with you?",
        "What are you glad you tried?",
        "What did you enjoy with your senses?",
        "What changed your perspective a little?",
        "What did you do for someone else?",
        "What are you grateful you did not rush?",
        "What place was part of your story today?",
        "What tiny achievement deserves remembering?",
        "What did you let go of?",
        "What did you look forward to?",
        "What helped you feel at home?",
        "What was the quietest good part of today?",
        "What question are you carrying into tomorrow?",
        "What did you do differently?",
        "Who did you think about today?",
        "What restored a little of your energy?",
        "What is one detail that a photo would miss?",
        "What did you finish, begin, or return to?",
        "What did you discover about yourself?",
        "What felt worth the effort?",
        "What would you like to do again?",
        "What small kindness did you notice?",
        "What was a welcome interruption?",
        "What brought you back to the present?",
        "What did the weather add to your day?",
        "What is one thing you want to remember about someone?",
        "What did you choose to keep simple?",
        "What made you curious?",
        "What was better than you expected?",
        "What did you read, hear, or see that stayed with you?",
        "What did you appreciate about your surroundings?",
        "What was a small adventure?",
        "What did you give yourself permission to do?",
        "What took courage, even a little?",
        "What is today's story in one sentence?",
        "What helped you through an ordinary task?",
        "What are you glad was part of today?",
    )

    fun forDate(date: LocalDate, variation: Int = 0): String {
        val stableDay = date.withYear(2000).dayOfYear - 1
        return questions[Math.floorMod(stableDay + variation, questions.size)]
    }
}
