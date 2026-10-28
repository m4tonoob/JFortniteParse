package me.fungames.jfortniteparse.ue4.objects.engine

import me.fungames.jfortniteparse.ue4.objects.core.misc.FFrameRate
import me.fungames.jfortniteparse.ue4.objects.uobject.FName
import me.fungames.jfortniteparse.ue4.reader.FArchive
import me.fungames.jfortniteparse.ue4.versions.GAME_UE5
import me.fungames.jfortniteparse.ue4.writer.FArchiveWriter

// Uncooked values carry per-platform overrides (CUE4Parse TPerPlatformProperty).
private inline fun <T> readPerPlatform(Ar: FArchive, cooked: Boolean, readValue: () -> T): Map<FName, T>? {
    if (cooked || !(Ar.game >= GAME_UE5(8) || Ar.isFilterEditorOnly)) return null
    return (0 until Ar.readInt32()).associate { Ar.readFName() to readValue() }
}

private inline fun <T> writePerPlatform(Ar: FArchiveWriter, perPlatform: Map<FName, T>?, writeValue: (T) -> Unit) {
    if (perPlatform == null) return
    Ar.writeInt32(perPlatform.size)
    perPlatform.forEach { (platform, value) ->
        Ar.writeFName(platform)
        writeValue(value)
    }
}

class FPerPlatformInt {
    var cooked: Boolean
    var value: Int
    var perPlatform: Map<FName, Int>? = null

    constructor(Ar: FArchive) {
        cooked = Ar.readBoolean()
        value = Ar.readInt32()
        perPlatform = readPerPlatform(Ar, cooked) { Ar.readInt32() }
    }

    fun serialize(Ar: FArchiveWriter) {
        Ar.writeBoolean(cooked)
        Ar.writeInt32(value)
        writePerPlatform(Ar, perPlatform) { Ar.writeInt32(it) }
    }

    constructor(cooked: Boolean, value: Int) {
        this.cooked = cooked
        this.value = value
    }
}

class FPerPlatformFloat {
    var cooked: Boolean
    var value: Float
    var perPlatform: Map<FName, Float>? = null

    constructor(Ar: FArchive) {
        cooked = Ar.readBoolean()
        value = Ar.readFloat32()
        perPlatform = readPerPlatform(Ar, cooked) { Ar.readFloat32() }
    }

    fun serialize(Ar: FArchiveWriter) {
        Ar.writeBoolean(cooked)
        Ar.writeFloat32(value)
        writePerPlatform(Ar, perPlatform) { Ar.writeFloat32(it) }
    }

    constructor(cooked: Boolean, value: Float) {
        this.cooked = cooked
        this.value = value
    }
}

class FPerPlatformFString {
    var cooked: Boolean
    var value: String
    var perPlatform: Map<FName, String>? = null

    constructor(Ar: FArchive) {
        cooked = Ar.readBoolean()
        value = Ar.readString()
        perPlatform = readPerPlatform(Ar, cooked) { Ar.readString() }
    }

    fun serialize(Ar: FArchiveWriter) {
        Ar.writeBoolean(cooked)
        Ar.writeString(value)
        writePerPlatform(Ar, perPlatform) { Ar.writeString(it) }
    }

    constructor(cooked: Boolean, value: String) {
        this.cooked = cooked
        this.value = value
    }
}

class FPerPlatformFrameRate {
    var cooked: Boolean
    var value: FFrameRate
    var perPlatform: Map<FName, FFrameRate>? = null

    constructor(Ar: FArchive) {
        cooked = Ar.readBoolean()
        value = FFrameRate(Ar.readInt32(), Ar.readInt32())
        perPlatform = readPerPlatform(Ar, cooked) { FFrameRate(Ar.readInt32(), Ar.readInt32()) }
    }

    fun serialize(Ar: FArchiveWriter) {
        Ar.writeBoolean(cooked)
        Ar.writeInt32(value.numerator)
        Ar.writeInt32(value.denominator)
        writePerPlatform(Ar, perPlatform) {
            Ar.writeInt32(it.numerator)
            Ar.writeInt32(it.denominator)
        }
    }

    constructor(cooked: Boolean, value: FFrameRate) {
        this.cooked = cooked
        this.value = value
    }
}

class FPerPlatformBool {
    var cooked: Boolean
    var value: Boolean
    var perPlatform: Map<FName, Boolean>? = null

    constructor(Ar: FArchive) {
        cooked = Ar.readBoolean()
        value = Ar.readBoolean()
        perPlatform = readPerPlatform(Ar, cooked) { Ar.readBoolean() }
    }

    fun serialize(Ar: FArchiveWriter) {
        Ar.writeBoolean(cooked)
        Ar.writeBoolean(value)
        writePerPlatform(Ar, perPlatform) { Ar.writeBoolean(it) }
    }

    constructor(cooked: Boolean, value: Boolean) {
        this.cooked = cooked
        this.value = value
    }
}
