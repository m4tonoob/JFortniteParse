package me.fungames.jfortniteparse.ue4.assets.objects

import me.fungames.jfortniteparse.LOG_JFP
import me.fungames.jfortniteparse.ue4.assets.reader.FAssetArchive
import me.fungames.jfortniteparse.ue4.assets.writer.FAssetArchiveWriter
import me.fungames.jfortniteparse.ue4.versions.EUnrealEngineObjectUE5Version.PROPERTY_TAG_COMPLETE_TYPE_NAME

class UScriptArray {
    var innerTag: FPropertyTag? = null
    val contents: MutableList<FProperty>

    constructor(Ar: FAssetArchive, typeData: PropertyType) {
        val elementCount = Ar.readInt32()
        val innerType = typeData.innerType!!
        val type = innerType.type.text
        if (!Ar.useUnversionedPropertySerialization) {
            if (Ar.ver >= PROPERTY_TAG_COMPLETE_TYPE_NAME && (type == "StructProperty" || type == "ArrayProperty")) {
                // UE 5.4+: type info is already embedded in typeData from FPropertyTypeName, no separate inner tag
            } else if (type == "StructProperty" || type == "ArrayProperty") {
                innerTag = FPropertyTag(Ar, false)
            }
        }
        contents = ArrayList(elementCount)
        for (i in 0 until elementCount) {
            val content = FProperty.readPropertyValue(Ar, innerTag?.typeData ?: innerType, FProperty.ReadType.ARRAY)
            if (content != null)
                contents.add(content)
            else
                LOG_JFP.warn("Failed to read array content of type $innerType at ${Ar.pos()}, index $i")
        }
    }

    fun serialize(Ar: FAssetArchiveWriter) {
        Ar.writeInt32(contents.size)
        innerTag?.serialize(Ar, false)
        contents.forEach {
            FProperty.writePropertyValue(Ar, it, FProperty.ReadType.ARRAY)
        }
    }

    override fun toString() = "UScriptArray{size=${contents.size}}"

    constructor(innerTag: FPropertyTag?, contents: MutableList<FProperty>) {
        this.innerTag = innerTag
        this.contents = contents
    }
}
