package com.faigenbloom.spartaculous.weight

class WeightDataSource(
    private val mongo: WeightMongo
) {
    suspend fun list(userId: String): List<WeightEntryDto> = mongo.list(userId)
    suspend fun add(userId: String, req: AddWeightRequest): WeightEntryDto = mongo.add(userId, req)
    suspend fun clear(userId: String): Long = mongo.clear(userId)
}
