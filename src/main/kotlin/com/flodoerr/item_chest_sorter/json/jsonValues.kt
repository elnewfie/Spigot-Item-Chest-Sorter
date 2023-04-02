package com.flodoerr.item_chest_sorter.json

import com.beust.klaxon.Json

data class Cords(var x: Int, var y: Int, var z: Int, var world: String? = null)

data class ChestLocation(var left: Cords, var right: Cords? = null)

data class Sender(var sid: String, var name: String, var cords: ChestLocation, var receiver: ArrayList<Receiver> = ArrayList(), var playerID: String? = null)

data class Receiver(var rid: String, var cords: ChestLocation, var playerID: String? = null)

data class ItemChest(
    var id: String,
    var name: String = "",
    var cords: ChestLocation, 
    var playerID: String? = null,
    var senders: ArrayList<String> = ArrayList(),
    var receivers: ArrayList<String> = ArrayList()
)

data class JSON(
    @Json(serializeNull = false)
    var sender: ArrayList<Sender>? = ArrayList(),   //Used only in version 1 format
    var version: Int = 1,
    var chests: ArrayList<ItemChest> = ArrayList()
)