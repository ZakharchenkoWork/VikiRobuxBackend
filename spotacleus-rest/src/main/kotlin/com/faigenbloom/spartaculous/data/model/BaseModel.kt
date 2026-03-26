package com.faigenbloom.spartaculous.data.model

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
abstract class BaseModel {
    @BsonId
    val id: String = ObjectId().toString()

    val createdAt: Long = System.currentTimeMillis()
    var updatedAt: Long = System.currentTimeMillis()
}
