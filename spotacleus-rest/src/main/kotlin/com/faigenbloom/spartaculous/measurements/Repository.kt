package com.faigenbloom.spartaculous.measurements

interface MeasurementsRepository {
    suspend fun list(userId: String, fromMs: Long? = null, toMs: Long? = null): List<MeasurementEntryDto>
    suspend fun upsert(userId: String, req: MeasurementUpsertDto): MeasurementEntryDto
    suspend fun delete(userId: String, id: String): Boolean

    // Новая модель целей по типам
    suspend fun listGoals(userId: String): List<MeasurementGoal>
    suspend fun upsertGoal(userId: String, goal: MeasurementGoalUpsertDto): MeasurementGoal
    suspend fun deleteGoal(userId: String, type: MeasurementType): Boolean

    suspend fun getGoal(userId: String): MeasurementsGoalDto?
    suspend fun setGoal(userId: String, goal: MeasurementsGoalDto): MeasurementsGoalDto
}
