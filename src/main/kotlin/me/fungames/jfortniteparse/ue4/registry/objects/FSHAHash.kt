package me.fungames.jfortniteparse.ue4.registry.objects

import me.fungames.jfortniteparse.ue4.reader.FArchive

class FSHAHash(Ar: FArchive) {
    val hash = if (Ar.readInt32() != 0) Ar.read(20) else null
}