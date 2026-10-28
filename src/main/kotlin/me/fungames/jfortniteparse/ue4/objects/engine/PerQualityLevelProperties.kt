package me.fungames.jfortniteparse.ue4.objects.engine

import me.fungames.jfortniteparse.ue4.assets.UProperty
import me.fungames.jfortniteparse.ue4.assets.UStruct
import me.fungames.jfortniteparse.ue4.reader.FArchive

@UStruct
class FPerQualityLevelInt {
    @UProperty("Default")
    var default: Int
    @UProperty("PerQuality")
    var perQuality: MutableMap<Int, Int>
    /*@UProperty("bIsEnabled")
    var isEnabled: Boolean*/

    constructor(Ar: FArchive) {
        val cooked = Ar.readBoolean()
        default = Ar.readInt32()
        perQuality = Ar.readTMap { Ar.readInt32() to Ar.readInt32() }
    }

    constructor(default: Int, perQuality: MutableMap<Int, Int> = mutableMapOf()) {
        this.default = default
        this.perQuality = perQuality
    }
}

@UStruct
class FPerQualityLevelFloat {
    @UProperty("Default")
    var default: Float
    @UProperty("PerQuality")
    var perQuality: MutableMap<Int, Float>

    constructor(Ar: FArchive) {
        Ar.readBoolean() // bCooked
        default = Ar.readFloat32()
        perQuality = Ar.readTMap { Ar.readInt32() to Ar.readFloat32() }
    }

    constructor(default: Float, perQuality: MutableMap<Int, Float> = mutableMapOf()) {
        this.default = default
        this.perQuality = perQuality
    }
}