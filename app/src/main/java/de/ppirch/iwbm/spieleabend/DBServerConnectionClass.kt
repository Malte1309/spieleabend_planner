package de.ppirch.iwbm.spieleabend

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Verwalten der Serverzugriffe durch Implementierung von OKHttp-Client
 */
class DBServerConnectionClass(context: Context) {
    private val db = DBHelperClass(context, null)
    private lateinit var cursor: Cursor //Empfängt die Rückgabe vom Auslesen der Datenbank

    /**
     * Aktuell gewaehlte Gruppe und zugehoerigen User auslesen als ContentValue-Objekt zurueckgeben
     */
    fun getCurrentUserGroup(): ContentValues {
        val returnValues = ContentValues()
        returnValues.put("group", "")
        returnValues.put("user", "")

        cursor = db.select()!!
        cursor.moveToFirst()

        val groupCount: Int //Anzahl Gruppen, in denen der User Mitglied ist.
        val currentGroupID: Int

        if (!cursor.isAfterLast) {
            cursor.moveToPosition(DBHelperClass.ID_GROUP_COUNT)
            groupCount = cursor.getString(DBHelperClass.VALUE_COLUMN_INDEX).toInt()

            if (groupCount > 0) {
                cursor.moveToPosition(DBHelperClass.ID_CURRENT_GROUP)
                currentGroupID = cursor.getString(DBHelperClass.VALUE_COLUMN_INDEX)
                    .toInt() + DBHelperClass.ID_FIRST_GROUP

                cursor.moveToPosition(currentGroupID)
                returnValues.clear()
                returnValues.put("group", cursor.getString(DBHelperClass.VALUE_COLUMN_INDEX))
                returnValues.put("user", cursor.getString(DBHelperClass.NAME_COLUMN_INDEX))
            }
        }
        cursor.close()
        return returnValues
    }

    /**
     * Beziehen der Daten vom WebFTP-Server
     * @param action Fallunterscheidung im PHP-Skript
     * @param phpScript Aufruf der Zieldatei (PHP-Skript)
     * @return JSONObject
     */
    fun getObject(action: String, phpScript: String, gameOrMealType: String = ""): JSONObject {
        var group = ""
        var user = ""
        val userGroupObject = getCurrentUserGroup()
        if (userGroupObject.containsKey("user") && userGroupObject.containsKey("group")) {
            group = userGroupObject.getAsString("group")
            user = userGroupObject.getAsString("user")
        }

        val client = OkHttpClient()
        val url = "https://spieleabend.open-serv.info/$phpScript.php"
        val body: FormBody = FormBody.Builder(StandardCharsets.UTF_8)
            .add("access", "spieleabend.iwbm@access")
            .add("action", action)
            .add("group", group)
            .add("user", user)
            .add("type", gameOrMealType)
            .build()

        val request: Request = Request
            .Builder()
            .url(url)
            .post(body)
            .build()

        var responsObjekt = JSONObject()

        if (group.isNotEmpty()) {
            try {
                val response = client.newCall(request).execute().body!!.string()
                if (response.isNotEmpty() && response != "{}" && response != "[]") {
                    responsObjekt = JSONObject(response)
                } else {
                    responsObjekt.put("fehler", "Empty Response")
                }
                responsObjekt.put("user", user)
                responsObjekt.put("group", group)
            } catch (error: JSONException) {
                Log.e("Error in DBServerConnectionClass", "Error: $error")
                responsObjekt.put("fehler", "JSON")
            } catch (error: IOException) {
                Log.e("Error in DBServerConnectionClass", "Error: $error")
                responsObjekt.put("fehler", "Server")
            } catch (error: Exception) {
                Log.e("Error in DBServerConnectionClass", "Error: $error")
                responsObjekt.put("fehler", "Unbekannt")
            }
        } else {
            responsObjekt.put("fehler", "No Group")
        }

        println("Antwort: $responsObjekt")

        return responsObjekt
    }

    /**
     * Senden der Daten vom WebFTP-Server
     * @param action Fallunterscheidung im PHP-Skript
     * @param phpScript Aufruf der Zieldatei (PHP-Skript)
     * @param game Senden eines Spiels
     * @param meal Senden eines Essens
     * @param extraData Zusaetzliche Informationen wie neuer Name beim Namenswechsel
     * @return String als Response
     */
    fun sendObject(
        action: String,
        phpScript: String,
        game: String = "",
        meal: String = "",
        extraData: String = ""
    ): String {
        var group = ""
        var user = ""
        val userGroupObject = getCurrentUserGroup()
        if (userGroupObject.containsKey("user") && userGroupObject.containsKey("group")) {
            group = userGroupObject.getAsString("group")
            user = userGroupObject.getAsString("user")
        }

        val client = OkHttpClient()
        val url = "https://spieleabend.open-serv.info/$phpScript.php"
        val body: FormBody = FormBody.Builder(StandardCharsets.UTF_8)
            .add("access", "spieleabend.iwbm@access")
            .add("action", action)
            .add("group", group)
            .add("user", user)
            .add("game", game)
            .add("meal", meal)
            .add("extraData", extraData)
            .build()

        val request: Request = Request
            .Builder()
            .url(url)
            .post(body)
            .build()

        return if (group.isNotEmpty()) {
            try {
                client.newCall(request).execute().body!!.string()
            } catch (error: IOException) {
                Log.e("Error in DBServerConnectionClass", "Error: $error")
                "Fehler: Server"
            } catch (error: Exception) {
                Log.e("Error in DBServerConnectionClass", "Error: $error")
                "Fehler: Unbekannt"
            }
        } else {
            "Fehler: No Group"
        }
    }
}