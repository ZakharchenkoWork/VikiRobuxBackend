package com.faigenbloom.spartaculous.common

import kotlinx.serialization.Serializable

@Serializable
data class Wrapper<T>(val list: List<T>)

@Serializable
class SUCCESS(val ok: Boolean = true)

@Serializable
class MESSAGE(val message: String)
