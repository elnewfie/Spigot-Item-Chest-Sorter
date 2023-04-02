package com.flodoerr.item_chest_sorter.json

import com.beust.klaxon.Klaxon
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.Bukkit
import java.util.UUID
import java.io.File
import java.nio.file.Paths
import kotlin.collections.ArrayList

class JsonHelper(dataFolder: File, commandSender: ConsoleCommandSender? = null, private val performanceMode: Boolean) {

    private val jsonFile = Paths.get(dataFolder.absolutePath, "chests.json").toFile()
    private val doNotTouchFile = Paths.get(dataFolder.absolutePath, "README (don't touch the json file if you don't know what you are doing)").toFile()

    private var cachedJSON: JSON? = null

    init {
        if(!dataFolder.exists()){
            dataFolder.mkdir()
        }
        if(!jsonFile.exists() || jsonFile.readText() == ""){
            jsonFile.writeText(Klaxon().toJsonString(JSON()))
            commandSender?.sendMessage("created json file")
        }
        doNotTouchFile.writeText("Don't touch the json file, if you don't know what you are doing! Really. Don't do it. You may edit the config.yml.")
    }

    /**
     * generates a unique id for a chest based on
     * its coordinates and world location.
     * @param cords coordinates of a potential chest
     *
     * @author elnewfie
     */

    fun generateId(cords: ChestLocation): String {
        return "${cords.left.x}~${cords.left.y}~${cords.left.z}~${Bukkit.getServer().getWorld(UUID.fromString(cords.left.world))!!.name}"
    }

    /**
     * adds the specified chest to the list of tracked chests
     * @param chest the chest to track
     * @return true if added, false if chest is already tracked
     *
     * @author elnewfie
     */
    fun addChest(chest: ItemChest): Boolean {
        val json = getJSON()

        if(json.chests.firstOrNull{ it.id == chest.id} == null)  {
            json.chests.add(chest)
            return true
        }

        return false
    }

    /**
     * returns the chest matching the specified id
     * @param id unique id of the chest
     * @return instance of ItemChest if found, otherwise null
     *
     * @author elnewfie
     */
    fun getChestById(id: String): ItemChest? {
        return getJSON().chests.firstOrNull{ it.id == id}
    }

    /**
     * returns the ItemChest instance at the specified coordinates
     * @param cords coordinates of chest
     * @return ItemChest instance if found, otherwise null
     *
     * @author elnewfie
     */
    fun getChestByCords(cords: Cords): ItemChest? {
        return getJSON().chests.firstOrNull{
            it.cords.left == cords ||
            it.cords.right == cords
        }
    }

    /**
     * indicates if the specified chest is a sender
     * (has receiver chests linked to it)
     * @param id id of chest to check
     * @return true if chest exists and has receivers, false if not
     *
     * @author elnewfie
     */
    fun isSender(id: String): Boolean {
        val chest = getChestById(id)

        return chest != null && chest.receivers.isNotEmpty()
    }

    fun isReceiver(chest: ItemChest?): Boolean {
        return chest != null && chest.senders.isNotEmpty()
    }

    /**
     * returns all saved sender
     * @return List of sender
     *
     * @author Flo Dörr
     */
    fun getSender(): List<ItemChest> {
        return getJSON().chests.filter{ it.receivers.isNotEmpty() }
    }

    fun addReceiverToSender(receiverId: String, senderId: String): Boolean {
        val sender = getChestById(senderId)
        val receiver = getChestById(receiverId)

        if(sender != null && receiver != null) {
            if(sender.receivers.firstOrNull{ it == receiverId } == null) {
                sender.receivers.add(receiverId)
            }

            if(receiver.senders.firstOrNull{ it == senderId}  == null) {
                receiver.senders.add(senderId)
            }

            return true
        }

        return false
    }

    fun removeChest(chestId: String): Boolean {
        val json = getJSON()

        val chest = json.chests.firstOrNull{ it.id == chestId }
        if(chest != null) {
            // Remove any existing linkages
            for(testChest in json.chests) {
                testChest.senders.remove(chestId)
                testChest.receivers.remove(chestId)
            }

            //Remove the chest
            json.chests.remove(chest)
            saveJSONIfNecessary(json)
            return true
        }

        return false
    }

    /**
     * reads the json from disk if not already cached
     * @return JSON object
     *
     * @author Flo Dörr
     */
    private fun getJSON(): JSON {
        if(cachedJSON == null) {
            cachedJSON = Klaxon().parse<JSON>(jsonFile.readText())!!
        }
        return cachedJSON as JSON
    }

    /**
     * saves json to drive if not in performance mode
     * @param json JSON object to be potentially saved
     * @return true saved
     *
     * @author Flo Dörr
     */
    private fun saveJSONIfNecessary(json: JSON): Boolean {
        if(performanceMode) {
            return false
        }
        return saveJSON(json)
    }

    /**
     * writes JSON object back to disk
     * @param json JSON object to be saved
     * @return true if saved successfully
     *
     * @author Flo Dörr
     */
    private fun saveJSON(json: JSON): Boolean {
        return try {
            jsonFile.writeText(
                Klaxon().toJsonString(
                    json
                )
            )
            cachedJSON = json
            true
        }catch (exception: Exception) {
            println(exception)
            false
        }
    }

    /**
     * writes JSON object back to disk
     * @return true if saved successfully
     *
     * @author Flo Dörr
     */
    fun saveJSON(): Boolean {
        if (cachedJSON == null) {
            return false
        }
        return saveJSON(cachedJSON!!)
    }

    /**
     * reviews the saved JSON format and updates to the latest
     * supported format used by the plugin.
     *
     * @author elnewfie
     */
    fun migrateJSON(defaultWorld: String) {
        var json: JSON = getJSON()

        if (json.version < 2 ) {
            //Upgrade to new format
            for(sender in json.sender!!) {
                //Convert to ItemChest and generate new ID
                val sChest = ItemChest(id=generateId(sender.cords), name=sender.name, cords=sender.cords, playerID=sender.playerID)

                addChest(sChest)

                for(receiver in sender.receiver) {
                    val rChest = ItemChest(id=generateId(receiver.cords), cords=receiver.cords, playerID=receiver.playerID)

                    addChest(rChest)

                    if(!addReceiverToSender(rChest.id, sChest.id)) {
                        println("unable to make sender/receiver relationship between chests " + sChest.id + " and " + rChest.id)
                    }
                }
            }

            for(chest in json.chests) {
                if(chest.cords.left.world == null) {
                    chest.cords.left.world = defaultWorld
                    chest.cords.right?.world = defaultWorld
                }
            }

            json.sender = null
            json.version = 2
        }

        //As future changes are made to the JSON format, add them here and increase the version

        saveJSON(json)
    }

    /**
     * gets the count of registered chest by a players uuid. Counts senders and receivers.
     * @return count of registered chests
     *
     * @author Flo Dörr
     */
    fun getChestCountByPlayer(playerUUID: String): Int {
        return getJSON().chests.count{ it.playerID == playerUUID }
    }
}