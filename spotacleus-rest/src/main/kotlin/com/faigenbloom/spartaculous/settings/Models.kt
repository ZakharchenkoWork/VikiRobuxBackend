package com.faigenbloom.spartaculous.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Enums (serialized in lowercase to match frontend)
@Serializable
enum class Gender {
    @SerialName("unspecified") UNSPECIFIED,
    @SerialName("male") MALE,
    @SerialName("female") FEMALE,
    @SerialName("non_binary") NON_BINARY
}

@Serializable
enum class WeightUnit {
    @SerialName("kg") KG,
    @SerialName("lb") LB
}

@Serializable
enum class WaterUnit {
    @SerialName("liters") LITERS,
    @SerialName("ounces") OUNCES
}

@Serializable
enum class WeekStart {
    @SerialName("monday") MONDAY,
    @SerialName("sunday") SUNDAY
}

@Serializable
enum class DateFormat {
    @SerialName("dd_mm_yyyy") DD_MM_YYYY,
    @SerialName("mm_dd_yyyy") MM_DD_YYYY
}

@Serializable
enum class Language {
    @SerialName("ru") RU,
    @SerialName("en") EN
}

@Serializable
enum class TipsIntensity {
    @SerialName("low") LOW,
    @SerialName("normal") NORMAL,
    @SerialName("high") HIGH
}

@Serializable
enum class PremiumType {
    @SerialName("free") FREE,
    @SerialName("active") ACTIVE
}

// Profile
@Serializable
data class ProfileDto(
    val nickname: String,
    val gender: Gender,
    val heightCm: Int,
    val birthDate: String? = null, // ISO date YYYY-MM-DD (optional if ageYears provided)
    val ageYears: Int? = null
)

// Cycle
@Serializable
data class CycleSettingsDto(
    val enabled: Boolean,
    val cycleLengthDays: Int,
    val periodLengthDays: Int
)

// Preferences
@Serializable
data class PreferencesDto(
    val weightUnit: WeightUnit,
    val waterUnit: WaterUnit,
    val weekStart: WeekStart,
    val dateFormat: DateFormat,
    val language: Language
)

// Appearance
@Serializable
data class AppearanceSettingsDto(
    val darkTheme: Boolean
)

// Reminders
@Serializable
data class ReminderSettingsDto(
    val enabled: Boolean,
    val soundEnabled: Boolean
)

// Tips
@Serializable
data class TipsSettingsDto(
    val enabled: Boolean,
    val intensity: TipsIntensity
)

// Premium
@Serializable
data class PremiumStatusDto(
    val type: PremiumType,
    val until: String? = null // ISO date YYYY-MM-DD when active
)

// About
@Serializable
data class AboutInfoDto(
    val appVersion: String,
    val build: String,
    val privacyPolicyUrl: String,
    val termsUrl: String
)

// Support
@Serializable
data class SupportTicketRequest(
    val message: String,
    val email: String
)
