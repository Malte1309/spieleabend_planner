package de.ppirch.iwbm.spieleabend

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import de.ppirch.iwbm.spieleabend.databinding.DashboardLayoutBinding
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Diese Avtivity ist die Startseite der App und bietet einen Ueberblick ueber die wichtigsten Informationen
 * des naechsten Spieleabends. Erzeugen eines Navigationsgeruests durch das Menue.
 */
class DashboardActivity : AppCompatActivity() {
    private lateinit var binding: DashboardLayoutBinding

    private val scope = CoroutineScope(Dispatchers.IO)

    private var newMessage: Boolean = false //Prüf-Variable, ob neue Nachrichten vorhanden sind

    private val connection = DBServerConnectionClass(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DashboardLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar((binding.toolbarDashboard))

        binding.swipeLayout.setOnRefreshListener {
            scope.launch {
                refreshDashboard()
            }
            binding.swipeLayout.isRefreshing = false
        }

        binding.buttonSaveDate.setOnClickListener {
            val dateStartString = binding.fieldNextGameNight.text.toString()
            val dateStart = Calendar.getInstance()
            dateStart.set(
                dateStartString.subSequence(6, 10).toString().toInt(),
                dateStartString.subSequence(3, 5).toString().toInt() - 1,
                dateStartString.subSequence(0, 2).toString().toInt(),
                dateStartString.subSequence(11, 13).toString().toInt(),
                dateStartString.subSequence(14, 16).toString().toInt()
            )
            val dateEnd = Calendar.getInstance()
            dateEnd.set(
                dateStartString.subSequence(6, 10).toString().toInt(),
                dateStartString.subSequence(3, 5).toString().toInt() - 1,
                dateStartString.subSequence(0, 2).toString().toInt(),
                dateStartString.subSequence(11, 13).toString().toInt() + 3,
                dateStartString.subSequence(14, 16).toString().toInt()
            )
            val host = binding.fieldHost.text.toString()
            val group = binding.fieldGroup.text.toString()
            val game = binding.fieldPlannedGame.text.toString()
            val meal = binding.fieldPlannedMeal.text.toString()

            //Impliziter Intent, um Termin (Datum) des naechsten Spielabends in den Kalender einzutragen
            val calenderIntent = Intent(Intent.ACTION_INSERT).apply {
                data = Events.CONTENT_URI
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dateStart.timeInMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, dateEnd.timeInMillis)
                putExtra(Events.TITLE, getString(R.string.game_night_event_text))
                putExtra(
                    Events.DESCRIPTION,
                    "${getString(R.string.group)}: $group\n${getString(R.string.host)}: $host\n${
                        getString(R.string.game)
                    }: $game\n${getString(R.string.meal)}: $meal"
                )
            }

            if (calenderIntent.resolveActivity(packageManager) != null) {
                startActivity(calenderIntent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        scope.launch {
            refreshDashboard()
        }
    }

    //Funktionen für das Menü
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.dashboard_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    /**
     *  Message-Symbol, falls neue Nachrichten nicht/vorliegen
     */
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val messageItem = menu!!.getItem(0)
        if (newMessage) {
            messageItem.setIcon(R.drawable.ic_new_message_sp)
        } else {
            messageItem.setIcon(R.drawable.ic_message_sp)
        }

        return super.onPrepareOptionsMenu(menu)
    }

    /**
     * Verbinden des Menue-Layouts mit den expliziten Intents, um die Navigation zu steuern
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_message -> {
                callMessageActivity()
                true
            }
            R.id.menu_planning -> {
                callPlanningActivity()
                true
            }
            R.id.menu_evaluation -> {
                callEvaluationActivity()
                true
            }
            R.id.menu_settings -> {
                callSettingsActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Aufruf der Evaluation-Activity (lastHost bewerten)
     */
    private fun callEvaluationActivity(
        putData: Boolean = false,
        key: String = "",
        data: String = ""
    ) {
        intent = Intent(this, EvaluationActivity::class.java)
        if (putData) {
            intent.putExtra(key, data)
        }
        startActivity(intent)
    }

    /**
     * Aufruf der Planning-Activity (Vorschlaege geben, einsehen und andere bewerten)
     */
    private fun callPlanningActivity(
        putData: Boolean = false,
        key: String = "",
        data: String = ""
    ) {
        intent = Intent(this, PlanningActivity::class.java)
        if (putData) {
            intent.putExtra(key, data)
        }
        startActivity(intent)
    }

    /**
     * Aufruf der Setting-Activity (Nutzer registrieren, Einstellung aendern insb. SQLite-DB)
     */
    private fun callSettingsActivity(
        putData: Boolean = false,
        key: String = "",
        data: String = ""
    ) {
        intent = Intent(this, SettingsActivity::class.java)
        if (putData) {
            intent.putExtra(key, data)
        }
        startActivity(intent)
    }

    /**
     * Aufruf der Message-Activity (Nachrichten abschicken & einsehen)
     */
    private fun callMessageActivity(putData: Boolean = false, key: String = "", data: String = "") {
        intent = Intent(this, MessageActivity::class.java)
        if (putData) {
            intent.putExtra(key, data)
        }
        startActivity(intent)
    }


    /**
     * Beziehend er Informationen & aktualisieren dieser, falls Aenderung vorhanden.
     * Erfolgt automatisch wegen dem onCreate()-Callback Aufruf
     */
    private suspend fun refreshDashboard() {
        val responseObject = connection.getObject(action = "get", phpScript = "dashboard")

        val currentGroup = binding.fieldGroup
        val textNextDate = binding.fieldNextGameNight
        val textHost = binding.fieldHost
        val textPlannedGame = binding.fieldPlannedGame
        val textPlannedMeal = binding.fieldPlannedMeal

        withContext(Dispatchers.Main) {
            if (!responseObject.has("fehler")) {
                try {
                    val responseDashboard = responseObject.getJSONObject("dashboard")

                    currentGroup.text = responseObject.getString("group")
                    textNextDate.text = responseDashboard.getString("date")
                    textHost.text = responseDashboard.getString("host")
                    textPlannedGame.text = responseDashboard.getString("game")
                    textPlannedMeal.text = responseDashboard.getString("meal")

                    if (responseObject["messages"].toString() != "[]" && responseObject["messages"].toString() != "{}" && responseObject["messages"].toString()
                            .isNotEmpty()
                    ) {
                        val messagesObjekt =
                            responseObject.getJSONObject("messages") //JSONObjekt aus dem Antwortobjekt herausgelöst, in dem alle Nachrichten drin sind.

                        //Prüfen, ob der User in allen Nachrichten enthalten ist
                        //true -> Nachricht gefunden, in denen der User nicht auftaucht, also neue Nachricht
                        // false -> User in allen Nachrichten selbst Autor, oder schon gelesen
                        newMessage = checkMessagesUser(
                            messagesObjekt,
                            connection.getCurrentUserGroup().getAsString("user")
                        )
                        invalidateOptionsMenu() //Menü neu laden, da dann das ICON für Benachrichtigungen neu gesetzt wird, je nachdem ob es neue Nachrichten gibt oder nicht.
                    } else {
                        newMessage = false
                        invalidateOptionsMenu()
                    }

                } catch (error: Exception) {
                    Log.e("Error in DashboardActivity", "Error: $error")
                    Toast.makeText(
                        this@DashboardActivity,
                        getString(R.string.unknown_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                currentGroup.text = ""
                textNextDate.text = ""
                textHost.text = ""
                textPlannedGame.text = ""
                textPlannedMeal.text = ""

                when (responseObject.get("fehler")) {
                    "JSON" -> Toast.makeText(
                        this@DashboardActivity,
                        getString(R.string.information_not_processed_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                    "Server" -> Toast.makeText(
                        this@DashboardActivity,
                        getString(R.string.server_not_found),
                        Toast.LENGTH_SHORT
                    ).show()
                    "No Group" -> Toast.makeText(
                        this@DashboardActivity,
                        getString(R.string.no_group_found),
                        Toast.LENGTH_SHORT
                    ).show()
                    "Unbekannt" -> Toast.makeText(
                        this@DashboardActivity,
                        getString(R.string.unknown_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Wandelt ein JSONArray in eine Liste um, auf die die Listen-Methoden angewendet werden koennen.
     */
    private fun jsonArrayToList(jsonArray: JSONArray?): MutableList<String> {
        val list = mutableListOf<String>()
        if (jsonArray!!.length() > 0) {
            for (index in 0 until jsonArray.length()) {
                list.add(jsonArray[index].toString())
            }
        }
        return list
    }

    /** Prüfen, ob eine Nachricht vom aktuellen User selbst kommt, oder von diesem schon gelesen wurde. In diesen Faellen wird false zurueckgegeben, ansonsten true.
     *   Als Parameter wird das Objekt erwartet, in dem alle Nachrichten der Gruppe drin sind, und der aktuelle User, fuer den geprueft wird.
     */
    private fun checkMessagesUser(messagesObjekt: JSONObject, user: String): Boolean {

        val messagesNames =
            jsonArrayToList(messagesObjekt.names()) //Liste der Namen, zu denen es NachrichtenObjekte gibt

        var messages: JSONObject //Nachrichten von bestimmtem User
        var messagesList: MutableList<String> //Liste der Nachrichten von bestimmtem User

        messagesNames.forEach { name ->
            if (name != user) {
                messages = messagesObjekt.getJSONObject(name)
                messagesList = jsonArrayToList(messagesObjekt.getJSONObject(name).names())
                messagesList.forEach { message ->
                    if (messages[message].toString() != user && !messages[message].toString()
                            .contains(" $user") && !messages[message].toString().contains("$user, ")
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }
}
