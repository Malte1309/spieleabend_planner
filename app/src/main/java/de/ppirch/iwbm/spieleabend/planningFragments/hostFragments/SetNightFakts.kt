package de.ppirch.iwbm.spieleabend.planningFragments.hostFragments

import android.os.Bundle
import android.util.Log

import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import de.ppirch.iwbm.spieleabend.DBServerConnectionClass
import de.ppirch.iwbm.spieleabend.R
import de.ppirch.iwbm.spieleabend.databinding.HostSetNightFaktsFragLayoutBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Diese Klasse ist nur vom jeweiligen (aktiven) Host nutzbar und ermoeglicht die Auswahl des Spiels/Essen
 * fuer das naechste Treffen
 */
class SetNightFakts : Fragment() {
    //Implementieren der ViewBinding-Klasse des Layouts
    private var _binding: HostSetNightFaktsFragLayoutBinding? = null
    private val binding get() = _binding!!

    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var connection: DBServerConnectionClass

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = HostSetNightFaktsFragLayoutBinding.inflate(inflater, container, false)

        connection = DBServerConnectionClass(requireContext())

        //in der Coroutine wird ueberprueft, ob der Anwender, der Host ist. Falls nicht, dann erhaellt
        // dieser einen Hinweis.
        scope.launch {
            if (checkUserIsHost()) {
                withContext(Dispatchers.Main) {
                    binding.headerTextSetNightFakts.text = getString(R.string.set_night_fakts)
                    binding.frameSetNightFakts.visibility = View.VISIBLE
                }
            } else {
                withContext(Dispatchers.Main) {
                    binding.headerTextSetNightFakts.text = getString(R.string.only_host_dash_text)
                    binding.frameSetNightFakts.visibility = View.GONE
                }
            }
        }

        binding.buttonSetFrameCommit.setOnClickListener {
            scope.launch {
                sendNightFaktsToServer(
                    binding.spinnerGames.selectedItem.toString(),
                    binding.spinnerMeals.selectedItem.toString()
                )
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
     * Diese Methode schickt die Auswahl an den Server, wobei diese wiederholt aufgerufenw erden kann und
     * dabei das vorherige Ergebnis ueberschreibt.
     * @param game ausgewaehlte Spiel (wird im Dashboard angezeigt)
     * @param meal ausgewaehlte Essen (wird im Dashboard angezeigt)
     */
    private suspend fun sendNightFaktsToServer(game: String, meal: String) {
        val response =
            connection.sendObject(action = "set", phpScript = "dashboard", game = game, meal = meal)

        withContext(Dispatchers.Main) {
            binding.spinnerGames.setSelection(0)
            binding.spinnerMeals.setSelection(0)

            //Verarbeitung der Response des Servers fuer ein Feedback an den Host
            Toast.makeText(
                requireContext(),
                when (response) {
                    "OK" -> getString(R.string.save_success_toast)
                    "Group not found" -> getString(R.string.group_not_found)
                    "Fehler: No Group" -> getString(R.string.no_group_found)
                    "Fehler: Server" -> getString(R.string.server_not_found)
                    "Please select meal & game together!" -> getString(R.string.host_select)
                    else -> getString(R.string.unknown_error)
                },
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Diese Methode erfragt beim Server, ob der Anwender ein Host ist, um ihm den Zugriff auf die
     * sendNightFaktsToServer-Methdoe zu ermoeglichen
     */
    private fun checkUserIsHost(): Boolean {
        val responseObject = connection.getObject(action = "get", phpScript = "dashboard")
        return try {
            val responseDashboard = responseObject.getJSONObject("dashboard")
            responseDashboard["host"] == responseObject["user"]
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Die Methode bezieht die geausserten Wuensche und generiert daraus eine Liste (Spinner), woraus
     * der Host mit @see "sendNightFaktsToServer" seine Auswahl trifft
     */
    private suspend fun getSuggestions() {
        val responseObject =
            connection.getObject(action = "getSuggestions", phpScript = "suggestions")

        withContext(Dispatchers.Main) {
            var games = mutableListOf<String>()
            var meals = mutableListOf<String>()
            if (!responseObject.has("fehler")) {
                try {
                    if (objectStringIsNotEmpty(responseObject["gameSuggestions"].toString())) {
                        games = jsonArrayToList(
                            responseObject.getJSONObject("gameSuggestions").names()!!
                        )
                    }
                    if (objectStringIsNotEmpty(responseObject["mealSuggestions"].toString())) {
                        meals = jsonArrayToList(
                            responseObject.getJSONObject("mealSuggestions").names()!!
                        )
                    }
                } catch (error: Exception) {
                    Log.e("Error in SetNightFaktsFragment in PlanningActivity", "Error: $error")
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.information_not_processed_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    when (responseObject.get("fehler")) {
                        "JSON" -> getString(R.string.information_not_processed_toast)
                        "Server" -> getString(R.string.server_not_found)
                        "No Group" -> getString(R.string.no_group_found)
                        else -> getString(R.string.unknown_error)
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }

            games.add(0, getString(R.string.game))
            meals.add(0, getString(R.string.meal))
            binding.spinnerGames.adapter =
                ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, games)
            binding.spinnerMeals.adapter =
                ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, meals)
        }
    }

    private fun jsonArrayToList(jsonArray: JSONArray): MutableList<String> {
        val list = mutableListOf<String>()

        for (index in 0 until jsonArray.length()) {
            list.add(jsonArray[index].toString())
        }
        return list
    }

    private fun objectStringIsNotEmpty(objectString: String): Boolean {
        return !(objectString == "{}"
                || objectString == "[]"
                || objectString == "")
    }
}