package com.example.a3tuz.api

fun calculateGameScore(hand: List<String>): Double {
    if (hand.isEmpty()) return 0.0
    if (hand.size == 3 && hand.all { it.startsWith("A") }) return 33.0
    fun getCardValue(card: String): Int {
        val rank = card.substring(0, card.length - 1)
        return when (rank) {
            "A" -> 11
            "K", "Q", "J", "10" -> 10
            else -> rank.toIntOrNull() ?: 0
        }
    }
    val suits = mutableMapOf('♣' to 0, '♦' to 0, '♥' to 0, '♠' to 0)
    hand.forEach { suits[it.last()] = suits.getOrDefault(it.last(), 0) + getCardValue(it) }
    var maxScore = suits.values.maxOrNull()?.toDouble() ?: 0.0
    if (hand.count { it.startsWith("A") } == 2 && 22.0 > maxScore) maxScore = 22.0
    hand.groupBy { it.substring(0, it.length - 1) }.forEach { (rank, cards) ->
        if (cards.size == 3) {
            val tripleScore = when (rank) {
                "A" -> 33.0
                "6" -> 32.5
                "7" -> 21.0
                "8" -> 24.0
                "9" -> 27.0
                "10", "J", "Q", "K" -> 30.0
                else -> 0.0
            }
            if (tripleScore > maxScore) maxScore = tripleScore
        }
    }
    return maxScore
}
