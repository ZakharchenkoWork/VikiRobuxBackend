package com.faigenbloom.spartaculous.weight

import kotlinx.coroutines.runBlocking

class WeightMongoRepository(
    private val dataSource: WeightDataSource
) : WeightRepository {
    override fun list(userId: String): List<WeightEntryDto> = runBlocking {
        dataSource.list(userId)
    }

    override fun add(userId: String, req: AddWeightRequest): WeightEntryDto = runBlocking {
        dataSource.add(userId, req)
    }

    override fun clear(userId: String): Long = runBlocking {
        dataSource.clear(userId)
    }
}
