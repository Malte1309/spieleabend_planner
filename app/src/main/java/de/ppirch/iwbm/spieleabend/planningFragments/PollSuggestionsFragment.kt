package de.ppirch.iwbm.spieleabend.planningFragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import de.ppirch.iwbm.spieleabend.DBServerConnectionClass
import de.ppirch.iwbm.spieleabend.R
import de.ppirch.iwbm.spieleabend.databinding.PollSuggestionsFragLayoutBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Diese Klasse bildet die Fachlogik, um fremde Vorschlaege (Spiel, Essen-) zu bewerten.
 * Dargestellt als TAB innerhalb der Planning-Activity (ist ein Fragment)
 */
class PollSuggestionsFragment : Fragment() {

    //Implementieren der ViewBinding-Klasse des Layouts
    private var _binding: PollSuggestionsFragLayoutBinding? = null
    private val binding get() = _binding!!

    private val scope = CoroutineScope(Dispatchers.IO)

    private lateinit var connection: DBServerConnectionClass

    private val suggestionsNamesList = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PollSuggestionsFragLayoutBinding.inflate(inflater, container, false)

        connection = DBServerConnectionClass(requireContext())

        //Coroutine, um neue Vorschlaege zu laden, falls vorhanden
        scope.launch { getSuggestions() }

        binding.swipeLayout.setOnRefreshListener {
            scope.launch { getSuggestions() }
            binding.swipeLayout.isRefreshing = false
        }

        binding.buttonRateSuggestionsCommit.setOnClickListener {
            val frame = binding.suggestionsLayoutFrame

            //Liste in der die Werte der Bewertungen gespeichert werden. Diese werden dann an den Server geschickt.
            val ratingList = mutableListOf<Int>()
            for (index in suggestionsNamesList.indices) {
                ratingList.add(frame.findViewWithTag<RatingBar>(suggestionsNamesList[index]).rating.toInt())
            }

            scope.launch {
                sendPoll(ratingList)
                getSuggestions() //Liste der offenen Bewertungen aktualisieren
            }
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        scope.launch {
            getSuggestions()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Beziehen der Vorschlaege als JSON der gewaehlten Gruppe vom WebFTP-Server (suggestions.php)
     */
    private suspend fun getSuggestions() {
        val user = connection.getCurrentUserGroup()["user"].toString()
        val responseObject =
            connection.getObject(action = "getSuggestions", phpScript = "suggestions")

        withContext(Dispatchers.Main) {
            if (!responseObject.has("fehler")) {
                try {
                    var gameSuggestions = JSONObject()
                    if (objectStringIsNotEmpty(responseObject["gameSuggestions"].toString())) {
                        gameSuggestions = responseObject.getJSONObject("gameSuggestions")
                    }

                    var mealSuggestions = JSONObject()
                    if (objectStringIsNotEmpty(responseObject["mealSuggestions"].toString())) {
                        mealSuggestions = responseObject.getJSONObject("mealSuggestions")
                    }

                    val suggestions = unionJSONObjekts(gameSuggestions, mealSuggestions)
                    var suggestionNames = mutableListOf<String>()
                    if (suggestions.length() > 0) {
                        suggestionNames = jsonArrayToList(suggestions.names())
                    }

                    val frame = binding.suggestionsLayoutFrame
                    binding.noNewSuggestionText.visibility = View.GONE
                    frame.removeAllViews()
                    suggestionsNamesList.clear()

                    if (suggestionNames.isNotEmpty()) {
                        suggestionNames.indices.forEach {
                            var userSuggested = mutableListOf<String>()
                            if (suggestions[suggestionNames[it]].toString().contains(", ")) {
                                userSuggested = suggestions[suggestionNames[it]].toString()
                                    .split(", ") as MutableList<String>
                            } else {
                                userSuggested.add(suggestions[suggestionNames[it]].toString())
                            }

                            if (!userSuggested.contains(user)) {
                                val textView =
                                    TextView(requireContext(), null, 0, R.style.pollSuggestionText)
                                textView.layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                textView.text = suggestionNames[it]
                                suggestionsNamesList.add(suggestionNames[it])
                                frame.addView(textView)

                                val ratingBar = RatingBar(
                                    requireContext(),
                                    null,
                                    0,
                                    R.style.pollSuggestionRating
                                )
                                ratingBar.layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                ratingBar.tag = suggestionNames[it]
                                frame.addView(ratingBar)
                            }
                        }
                        if (suggestionsNamesList.isEmpty()) {
                            binding.noNewSuggestionText.visibility = View.VISIBLE
                        }
                    } else {
                        binding.noNewSuggestionText.visibility = View.VISIBLE
                    }
                } catch (error: Exception) {
                    Log.e("Error in PollSug in PlanAct", "Error: $error")
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.information_not_processed_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                when (responseObject.get("fehler")) {
                    "JSON" -> Toast.makeText(
                        requireContext(),
                        getString(R.string.information_not_processed_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                    "Server" -> Toast.makeText(
                        requireContext(),
                        getString(R.string.server_not_found),
                        Toast.LENGTH_SHORT
                    ).show()
                    "No Group" -> Toast.makeText(
                        requireContext(),
                        getString(R.string.no_group_found),
                        Toast.LENGTH_SHORT
                    ).show()
                    "Unbekannt" -> Toast.makeText(
                        requireContext(),
                        getString(R.string.unknown_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Senden der Bewertung an den WebFTP-Server (suggestions.php)
     */
    private suspend fun sendPoll(ratings: MutableList<Int>) {
        var group = ""
        var user = ""
        val userGroupObject = connection.getCurrentUserGroup()
        if (userGroupObject.containsKey("user") && userGroupObject.containsKey("group")) {
            group = userGroupObject.getAsString("group")
            user = userGroupObject.getAsString("user")
        }

        //Da hier kein StandardBody gesendet werden kann, sondern je nach Anzahl gesendeter Bewertungen der Body variiert,
        // wird hier der Request direkt und ohne die DBServerConnectionKlasse durchgeführt
        val client = OkHttpClient()
        val url = "https://spieleabend.open-serv.info/suggestions.php"
        val bodyValues = FormBody.Builder(StandardCharsets.UTF_8)
            .add("access", "spieleabend.iwbm@access")
            .add("action", "poll")
            .add("group", group)
            .add("user", user)
            .add("countPolls", suggestionsNamesList.size.toString())

        //Für jede Bewertung wird ein eigenes Key-Value-Pair an den Body angehängt
        for (index in suggestionsNamesList.indices) {
            if (ratings[index] > 0) {
                bodyValues.add(suggestionsNamesList[index], ratings[index].toString())
            }
        }
        val body = bodyValues.build()

        val request: Request = Request
            .Builder()
            .url(url)
            .post(body)
            .build()

        val response: String = try {
            client.newCall(request).execute().body!!.string()
        } catch (error: IOException) {
            Log.e("Error in PollSug in PlanAct", "Error: $error")
            "Fehler: Server"
        } catch (error: Exception) {
            Log.e("Error in PollSug in PlanAct", "Error: $error")
            "Fehler: Unbekannt"
        }

        //erzeugen von Toasts als Feedback abhaengig der Response des Servers
        withContext(Dispatchers.Main) {
            Toast.makeText(
                requireContext(),
                when (response) {
                    "OK" -> "OK"
                    "Group not found" -> getString(R.string.no_group_found)
                    "Fehler: Server" -> getString(R.string.server_not_found)
                    else -> getString(R.string.unknown_error)
                },
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun jsonArrayToList(jsonArray: JSONArray?): MutableList<String> {
        val list = mutableListOf<String>()
        if (jsonArray!!.length() > 0) {
            for (index in 0 until jsonArray.length()) {
                list.add(jsonArray[index].toString())
            }
        }
        return list
    }

    private fun unionJSONObjekts(object1: JSONObject, object2: JSONObject): JSONObject {
        return if (object1.length() > 0) {
            if (object2.length() > 0) {
                val objekt2NameList = jsonArrayToList(object2.names())
                objekt2NameList.forEach {
                    object1.put(it, object2[it])
                }
                object1
            } else {
                object1
            }

        } else {
            return if (object2.length() > 0) {
                object2
            } else {
                (JSONObject())
            }
        }
    }

    private fun objectStringIsNotEmpty(objectString: String): Boolean {
        return !(objectString == "{}"
                || objectString == "[]"
                || objectString == "")
    }
}
