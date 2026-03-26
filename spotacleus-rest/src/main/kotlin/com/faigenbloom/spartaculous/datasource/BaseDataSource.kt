package com.faigenbloom.spartaculous.datasource

import com.faigenbloom.spartaculous.data.model.BaseModel
import org.litote.kmongo.coroutine.CoroutineDatabase

interface BaseDataSource<T : BaseModel> {
    suspend fun findAll(): List<T>
    suspend fun findById(id: String): T?
    suspend fun insert(item: T): T
    suspend fun update(id: String, item: T): Boolean
    suspend fun delete(id: String): Boolean
}

abstract class BaseDataSourceImpl<T : BaseModel>(
    protected val database: CoroutineDatabase,
    protected val collectionName: String
) : BaseDataSource<T> {

    override suspend fun findAll(): List<T> =
        throw NotImplementedError("Data source not implemented yet")

    override suspend fun findById(id: String): T? =
        throw NotImplementedError("Data source not implemented yet")

    override suspend fun insert(item: T): T =
        throw NotImplementedError("Data source not implemented yet")

    override suspend fun update(id: String, item: T): Boolean =
        throw NotImplementedError("Data source not implemented yet")

    override suspend fun delete(id: String): Boolean =
        throw NotImplementedError("Data source not implemented yet")
}
