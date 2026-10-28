package me.fungames.jfortniteparse.ue4.assets.mappings

import me.fungames.jfortniteparse.ue4.assets.exports.UScriptStruct

class TypeMappings(var types: MutableMap<String, UScriptStruct> = mutableMapOf(),
                   var enums: MutableMap<String, List<String>> = mutableMapOf(),
                   /** Real numeric values parallel to [enums], when the source stores them (usmap v4+). */
                   var enumNumbers: MutableMap<String, List<Long>> = mutableMapOf())
