package me.fungames.jfortniteparse.ue4.objects.core.math

/** Generic integer vectors (CUE4Parse TIntVector2/3/4) for the int, uint and int64 native structs. */
data class TIntVector2<T : Any>(val x: T, val y: T)

data class TIntVector3<T : Any>(val x: T, val y: T, val z: T)

data class TIntVector4<T : Any>(val x: T, val y: T, val z: T, val w: T)
