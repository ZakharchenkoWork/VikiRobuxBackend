package com.faigenbloom.spartaculous.settings

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap

interface SettingsRepository {
    // Profile
    fun getProfile(userId: String): ProfileDto
    fun updateProfile(userId: String, dto: ProfileDto): ProfileDto

    // Cycle
    fun getCycle(userId: String): CycleSettingsDto
    fun updateCycle(userId: String, dto: CycleSettingsDto)

    // Preferences
    fun getPreferences(userId: String): PreferencesDto
    fun updatePreferences(userId: String, dto: PreferencesDto)

    // Appearance
    fun getAppearance(userId: String): AppearanceSettingsDto
    fun updateAppearance(userId: String, dto: AppearanceSettingsDto)

    // Reminders
    fun getReminders(userId: String): ReminderSettingsDto
    fun updateReminders(userId: String, dto: ReminderSettingsDto)

    // Tips
    fun getTips(userId: String): TipsSettingsDto
    fun updateTips(userId: String, dto: TipsSettingsDto)

    // Premium
    fun getPremiumStatus(userId: String): PremiumStatusDto

    // About
    fun getAbout(): AboutInfoDto

    // Support
    fun submitSupportTicket(userId: String, req: SupportTicketRequest)
}

class InMemorySettingsRepository : SettingsRepository {
    private data class State(
        var profile: ProfileDto = ProfileDto(
            nickname = "User",
            gender = Gender.UNSPECIFIED,
            heightCm = 170,
            birthDate = "1990-01-01"
        ),
        var cycle: CycleSettingsDto = CycleSettingsDto(
            enabled = false,
            cycleLengthDays = 28,
            periodLengthDays = 5
        ),
        var preferences: PreferencesDto = PreferencesDto(
            weightUnit = WeightUnit.KG,
            waterUnit = WaterUnit.LITERS,
            weekStart = WeekStart.MONDAY,
            dateFormat = DateFormat.DD_MM_YYYY,
            language = Language.RU
        ),
        var appearance: AppearanceSettingsDto = AppearanceSettingsDto(darkTheme = false),
        var reminders: ReminderSettingsDto = ReminderSettingsDto(enabled = false, soundEnabled = true),
        var tips: TipsSettingsDto = TipsSettingsDto(enabled = true, intensity = TipsIntensity.NORMAL),
        var premium: PremiumStatusDto = PremiumStatusDto(type = PremiumType.FREE, until = null)
    )

    private val store = ConcurrentHashMap<String, State>()
    private val supportInbox = mutableListOf<Pair<String, SupportTicketRequest>>()

    private fun state(uid: String) = store.computeIfAbsent(uid) { State() }

    override fun getProfile(userId: String): ProfileDto = state(userId).profile

    override fun updateProfile(userId: String, dto: ProfileDto): ProfileDto {
        // Validate height
        if (dto.heightCm <= 0) throw IllegalArgumentException("heightCm must be positive")

        // Validate age or birth date (at least one must be present)
        val hasBirth = !dto.birthDate.isNullOrBlank()
        val hasAge = dto.ageYears != null
        if (!hasBirth && !hasAge) {
            throw IllegalArgumentException("Either birthDate or ageYears must be provided")
        }
        if (hasBirth) {
            parseDate(dto.birthDate!!)
        }
        if (hasAge) {
            val age = dto.ageYears!!
            if (age !in 1..120) throw IllegalArgumentException("ageYears must be between 1 and 120")
        }

        state(userId).profile = dto
        return dto
    }

    override fun getCycle(userId: String): CycleSettingsDto = state(userId).cycle

    override fun updateCycle(userId: String, dto: CycleSettingsDto) {
        if (dto.enabled) {
            if (dto.cycleLengthDays !in 15..60) throw IllegalArgumentException("cycleLengthDays must be between 15 and 60")
            if (dto.periodLengthDays !in 1..14) throw IllegalArgumentException("periodLengthDays must be between 1 and 14")
        }
        state(userId).cycle = dto
    }

    override fun getPreferences(userId: String): PreferencesDto = state(userId).preferences

    override fun updatePreferences(userId: String, dto: PreferencesDto) {
        state(userId).preferences = dto
    }

    override fun getAppearance(userId: String): AppearanceSettingsDto = state(userId).appearance

    override fun updateAppearance(userId: String, dto: AppearanceSettingsDto) {
        state(userId).appearance = dto
    }

    override fun getReminders(userId: String): ReminderSettingsDto = state(userId).reminders

    override fun updateReminders(userId: String, dto: ReminderSettingsDto) {
        state(userId).reminders = dto
    }

    override fun getTips(userId: String): TipsSettingsDto = state(userId).tips

    override fun updateTips(userId: String, dto: TipsSettingsDto) {
        state(userId).tips = dto
    }

    override fun getPremiumStatus(userId: String): PremiumStatusDto = state(userId).premium

    override fun getAbout(): AboutInfoDto = AboutInfoDto(
        appVersion = System.getenv("APP_VERSION") ?: "1.0",
        build = System.getenv("APP_BUILD") ?: "100",
        privacyPolicyUrl = System.getenv("PRIVACY_URL") ?: "https://example.com/privacy",
        termsUrl = System.getenv("TERMS_URL") ?: "https://example.com/terms"
    )

    override fun submitSupportTicket(userId: String, req: SupportTicketRequest) {
        if (req.message.isBlank()) throw IllegalArgumentException("message is required")
        if (!req.email.contains("@")) throw IllegalArgumentException("email is invalid")
        supportInbox += userId to req
    }

    private fun parseDate(s: String) = try { LocalDate.parse(s) } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Invalid date: must be ISO YYYY-MM-DD")
    }
}
