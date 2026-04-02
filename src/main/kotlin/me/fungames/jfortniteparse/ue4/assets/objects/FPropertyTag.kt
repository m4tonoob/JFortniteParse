package me.fungames.jfortniteparse.ue4.assets.objects

import me.fungames.jfortniteparse.LOG_JFP
import me.fungames.jfortniteparse.exceptions.ParserException
import me.fungames.jfortniteparse.ue4.assets.reader.FAssetArchive
import me.fungames.jfortniteparse.ue4.assets.writer.FAssetArchiveWriter
import me.fungames.jfortniteparse.ue4.objects.core.misc.FGuid
import me.fungames.jfortniteparse.ue4.objects.uobject.FName
import me.fungames.jfortniteparse.ue4.versions.*
import me.fungames.jfortniteparse.ue4.versions.EUnrealEngineObjectUE5Version.PROPERTY_TAG_COMPLETE_TYPE_NAME
import me.fungames.jfortniteparse.util.INDEX_NONE
import java.lang.reflect.Type

/**
 * Property tag extension flags
 */
enum class EPropertyTagExtension(val value: Int) {
    NoExtension(0x00),
    ReserveForFutureUse(0x01),
    OverridableInformation(0x02);

    companion object {
        fun hasFlag(flags: Int, flag: EPropertyTagExtension): Boolean {
            return (flags and flag.value) != 0
        }
    }
}

/**
 * UE 5.4+ property tag flags (packed into a single byte)
 */
object EPropertyTagFlags {
    const val HasArrayIndex = 0x01
    const val HasPropertyGuid = 0x02
    const val HasPropertyExtensions = 0x04
    const val HasBinaryOrNativeSerialize = 0x08
    const val BoolTrue = 0x10
    const val SkippedSerialize = 0x20
}

/**
 * A tag describing a class property, to aid in serialization.
 */
class FPropertyTag {
    // Transient.
    var prop: FProperty? = null // prop: FProperty

    // Variables.
    /** Type of property */
    lateinit var type: FName
    /** A boolean property's value (never need to serialize data for bool properties except here) */
    var boolVal: Boolean = false
    /** Name of property. */
    var name: FName
    /** Struct name if FStructProperty. */
    var structName = FName.NAME_None
    /** Enum name if FByteProperty or FEnumProperty */
    var enumName = FName.NAME_None
    /** Inner type if FArrayProperty, FSetProperty, or FMapProperty */
    var innerType = FName.NAME_None
    /** Value type if UMapProperty */
    var valueType = FName.NAME_None
    /** Property size. */
    var size: Int = 0
    /** Index if an array; else 0. */
    var arrayIndex = INDEX_NONE
    /** Location in stream of tag size member */
    var sizeOffset = -1L
    var structGuid: FGuid? = null
    var hasPropertyGuid: Boolean = false
    var propertyGuid: FGuid? = null

    var typeData: PropertyType? = null

    constructor(Ar: FAssetArchive, readData: Boolean) {
        name = Ar.readFName()
        if (!name.isNone()) {
            if (Ar.ver >= PROPERTY_TAG_COMPLETE_TYPE_NAME) {
                // UE 5.4+ new tagged property format
                val typeName = FPropertyTypeName(Ar)
                type = FName(typeName.getName())
                typeData = buildPropertyTypeFromTypeName(typeName)

                size = Ar.readInt32()
                val flags = Ar.read().toInt() and 0xFF

                boolVal = (flags and EPropertyTagFlags.BoolTrue) != 0
                if (boolVal) typeData!!.bool = true
                arrayIndex = if ((flags and EPropertyTagFlags.HasArrayIndex) != 0) Ar.readInt32() else 0
                hasPropertyGuid = (flags and EPropertyTagFlags.HasPropertyGuid) != 0
                if (hasPropertyGuid) propertyGuid = FGuid(Ar)

                if ((flags and EPropertyTagFlags.HasPropertyExtensions) != 0) {
                    val tagExtensions = Ar.read()
                    if (EPropertyTagExtension.hasFlag(tagExtensions, EPropertyTagExtension.OverridableInformation)) {
                        val overrideOperation = Ar.read() // EOverriddenPropertyOperation
                        val bExperimentalOverridableLogic = Ar.readBoolean()
                    }
                }

                // Copy struct/enum info back to tag fields for compatibility
                structName = typeData!!.structName
                enumName = typeData!!.enumName
                innerType = typeData!!.innerType?.type ?: FName.NAME_None
                valueType = typeData!!.valueType?.type ?: FName.NAME_None
            } else {
                // Legacy tagged property format
                type = Ar.readFName()
                size = Ar.readInt32()
                arrayIndex = Ar.readInt32()
                val tagType = type.text

                if (tagType == "StructProperty") {
                    structName = Ar.readFName()
                    if (Ar.ver >= VER_UE4_STRUCT_GUID_IN_PROPERTY_TAG)
                        structGuid = FGuid(Ar)
                } else if (tagType == "BoolProperty") {
                    boolVal = Ar.readFlag()
                } else if (tagType == "ByteProperty") {
                    enumName = Ar.readFName()
                } else if (tagType == "EnumProperty") {
                    enumName = Ar.readFName()
                } else if (tagType == "ArrayProperty") {
                    if (Ar.ver >= VER_UE4_ARRAY_PROPERTY_INNER_TAGS)
                        innerType = Ar.readFName()
                } else if (Ar.ver >= VER_UE4_PROPERTY_TAG_SET_MAP_SUPPORT) {
                    if (tagType == "SetProperty") {
                        innerType = Ar.readFName()
                    } else if (tagType == "MapProperty") {
                        innerType = Ar.readFName()
                        valueType = Ar.readFName()
                    }
                }

                if (Ar.ver >= VER_UE4_PROPERTY_GUID_IN_PROPERTY_TAG) {
                    hasPropertyGuid = Ar.readFlag()
                    if (hasPropertyGuid)
                        propertyGuid = FGuid(Ar)
                }

                if (FUE5MainStreamObjectVersion.get(Ar) >= FUE5MainStreamObjectVersion.PROPERTY_TAG_EXTENSION_AND_OVERRIDABLE_SERIALIZATION) {
                    val tagExtensions = Ar.read()
                    if (EPropertyTagExtension.hasFlag(tagExtensions, EPropertyTagExtension.OverridableInformation)) {
                        val overrideOperation = Ar.read()
                        val bExperimentalOverridableLogic = Ar.readBoolean()
                    }
                }

                typeData = PropertyType(this)
            }

            if (readData) {
                val pos = Ar.pos()
                val finalPos = pos + size
                try {
                    prop = FProperty.readPropertyValue(Ar, typeData!!, FProperty.ReadType.NORMAL)
                    if (finalPos != Ar.pos()) {
                        LOG_JFP.warn("FPropertyTagType $typeData $name was not read properly, pos ${Ar.pos()}, calculated pos $finalPos")
                    }
                } catch (e: ParserException) {
                    if (finalPos != Ar.pos()) {
                        LOG_JFP.warn("Failed to read FPropertyTagType $typeData $name, skipping it", e)
                    }
                } finally {
                    Ar.seek(finalPos)
                }
            }
        }
    }

    constructor(Ar: FAssetArchive, info: PropertyInfo, arrayIndex: Int, type: FProperty.ReadType) {
        name = FName(info.name)
        this.arrayIndex = arrayIndex
        val typeData = info.type
        this.typeData = typeData
        val pos = Ar.pos()
        try {
            prop = FProperty.readPropertyValue(Ar, typeData, type)
        } catch (e: ParserException) {
            throw ParserException("Failed to read FPropertyTagType $typeData $name", e)
        }
        size = Ar.pos() - pos
    }

    fun <T> getTagTypeValue(clazz: Class<T>, type: Type? = null): T? {
        if (prop == null)
            throw IllegalArgumentException("This tag was read without data")
        return prop?.getTagTypeValue(clazz, type)
    }

    inline fun <reified T> getTagTypeValue(): T? {
        if (prop == null)
            throw IllegalArgumentException("This tag was read without data")
        return prop?.getTagTypeValue<T>()
    }

    //@Deprecated(message = "Should not be used anymore, since its not able to process arrays and struct fallback", replaceWith = ReplaceWith("getTagTypeValue<T>"))
    fun getTagTypeValueLegacy() = prop?.getTagTypeValueLegacy() ?: throw IllegalArgumentException("This tag was read without data")

    fun setTagTypeValue(value: Any?) = prop?.setTagTypeValue(value)

    fun serialize(Ar: FAssetArchiveWriter, writeData: Boolean) {
        Ar.writeFName(name)
        if (name.text != "None") {
            Ar.writeFName(type)
            var tagTypeData: ByteArray? = null
            if (writeData) {
                //Recalculate the size of the tag and also save the serialized data
                val tempAr = Ar.setupByteArrayWriter()
                try {
                    FProperty.writePropertyValue(
                        tempAr,
                        prop ?: throw ParserException("FPropertyTagType is needed when trying to write it"),
                        FProperty.ReadType.NORMAL
                    )
                    Ar.writeInt32(tempAr.pos() - Ar.pos())
                    tagTypeData = tempAr.toByteArray()
                } catch (e: ParserException) {
                    throw ParserException("Error occurred while writing the FPropertyTagType $name ($type) ", Ar, e)
                }
            } else {
                Ar.writeInt32(size)
            }
            Ar.writeInt32(arrayIndex)
            // TODO tagData?.serialize(Ar)

            Ar.writeFlag(hasPropertyGuid)
            if (hasPropertyGuid)
                propertyGuid?.serialize(Ar)

            if (writeData) {
                if (tagTypeData != null) {
                    Ar.write(tagTypeData)
                }
            }
        }
    }

    override fun toString() = "${name.text}   -->   ${if (prop != null) getTagTypeValueLegacy() else "Failed to parse"}"

    companion object {
        /**
         * Converts a UE 5.4 FPropertyTypeName tree into JFP's PropertyType structure,
         * extracting struct names, enum names, inner/value types recursively.
         */
        fun buildPropertyTypeFromTypeName(typeName: FPropertyTypeName): PropertyType {
            val pt = PropertyType(FName(typeName.getName()))
            when (typeName.getName()) {
                "BoolProperty" -> pt.bool = false
                "StructProperty" -> {
                    typeName.getParameter(0)?.let { pt.structName = FName(it.getName()) }
                }
                "ByteProperty", "EnumProperty" -> {
                    typeName.getParameter(0)?.let { pt.enumName = FName(it.getName()) }
                }
                "ArrayProperty", "SetProperty", "OptionalProperty" -> {
                    typeName.getParameter(0)?.let {
                        pt.innerType = buildPropertyTypeFromTypeName(it)
                    }
                }
                "MapProperty" -> {
                    typeName.getParameter(0)?.let { pt.innerType = buildPropertyTypeFromTypeName(it) }
                    typeName.getParameter(1)?.let { pt.valueType = buildPropertyTypeFromTypeName(it) }
                }
            }
            return pt
        }
    }
}