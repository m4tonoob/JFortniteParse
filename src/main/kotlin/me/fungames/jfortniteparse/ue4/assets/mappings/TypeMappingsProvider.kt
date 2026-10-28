package me.fungames.jfortniteparse.ue4.assets.mappings

import me.fungames.jfortniteparse.ue4.assets.ObjectTypeRegistry
import me.fungames.jfortniteparse.ue4.assets.exports.UEnum
import me.fungames.jfortniteparse.ue4.assets.exports.UStruct
import me.fungames.jfortniteparse.ue4.objects.uobject.FName

abstract class TypeMappingsProvider {
    val mappings = TypeMappings()
    abstract fun reload(): Boolean

    open fun getStruct(structName: FName): UStruct? {
        val struct = mappings.types[structName.text]
            ?: return null
        // required to be assigned so classes with custom serializers can be read properly
        if (struct.structClass == null) {
            struct.structClass = ObjectTypeRegistry.get(structName.text)
        }
        return struct
    }

    open fun getEnumValues(enumName: FName) = mappings.enums[enumName.text] ?: mappings.enums['E' + enumName.text]

    /** Value names paired with their numbers; falls back to list position when no numbers were stored. */
    fun getEnumEntries(enumName: FName): List<Pair<String, Long>>? {
        val names = getEnumValues(enumName) ?: return null
        val numbers = mappings.enumNumbers[enumName.text] ?: mappings.enumNumbers['E' + enumName.text]
        return names.mapIndexed { i, name -> name to (numbers?.getOrNull(i) ?: i.toLong()) }
    }

    fun getEnum(enumName: FName): UEnum? {
        val entries = getEnumEntries(enumName) ?: return null
        val enum = UEnum()
        enum.name = enumName.text
        enum.names = Array(entries.size) { FName("$enumName::${entries[it].first}") to entries[it].second }
        return enum
    }
}