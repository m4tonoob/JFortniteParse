package me.fungames.jfortniteparse.ue4.assets.objects

import me.fungames.jfortniteparse.ue4.assets.reader.FAssetArchive
import me.fungames.jfortniteparse.ue4.objects.uobject.FName

/**
 * UE 5.4 property type name — a tree of (FName, innerCount) nodes
 * that encodes the complete type information for a property tag.
 *
 * For example, a MapProperty<NameProperty, ArrayProperty<StructProperty<FVector>>>
 * would be serialized as a flat list of nodes where each node's innerCount
 * indicates how many child type parameters it has.
 */
class FPropertyTypeName {
    val nodes: List<Node>

    data class Node(val name: FName, val innerCount: Int)

    constructor(Ar: FAssetArchive) {
        val nodeList = mutableListOf<Node>()
        var remaining = 1
        do {
            val name = Ar.readFName()
            val innerCount = Ar.readInt32()
            nodeList.add(Node(name, innerCount))
            remaining += innerCount - 1
        } while (remaining > 0)
        nodes = nodeList
    }

    /** Constructor from a sub-slice of nodes (for getParameter) */
    private constructor(nodeSlice: List<Node>) {
        nodes = nodeSlice
    }

    /** Get the top-level type name (e.g. "StructProperty", "IntProperty") */
    fun getName(): String = if (nodes.isEmpty()) "None" else nodes[0].name.text

    /** Get how many type parameters the top-level type has */
    fun getParameterCount(): Int = if (nodes.isEmpty()) 0 else nodes[0].innerCount

    /**
     * Get the type parameter at the given index.
     * For example, for MapProperty the key type is parameter 0 and value type is parameter 1.
     * Returns null if the index is out of range.
     */
    fun getParameter(paramIndex: Int): FPropertyTypeName? {
        if (nodes.isEmpty()) return null
        if (paramIndex < 0 || paramIndex >= nodes[0].innerCount) return null

        // Skip to the right parameter by walking past earlier parameters and their subtrees
        var param = 1
        var skip = paramIndex
        while (skip > 0) {
            skip += nodes[param].innerCount
            skip--
            param++
        }

        return if (param < nodes.size) FPropertyTypeName(nodes.subList(param, nodes.size)) else null
    }
}
