package kotlinx.io

import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readBytes

fun ByteReadPacket.readByteArray(): ByteArray = this.readBytes()
