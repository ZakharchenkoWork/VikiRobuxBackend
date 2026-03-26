package com.faigenbloom.spartaculous.routing

// Allows calling isBlank() on nullable strings in routes without failing validation
// Returning false for null lets partial updates (e.g., only recordedAtEpochMillis) pass through
fun String?.isBlank(): Boolean = this != null && this.trim().isEmpty()
