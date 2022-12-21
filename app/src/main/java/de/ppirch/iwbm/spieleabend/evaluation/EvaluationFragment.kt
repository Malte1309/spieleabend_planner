package de.ppirch.iwbm.spieleabend.evaluation

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import de.ppirch.iwbm.spieleabend.DBServerConnectionClass
import de.ppirch.iwbm.spieleabend.R
import de.ppirch.iwbm.spieleabend.databinding.EvaluationFragLayoutBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import java.nio.charset.StandardCharsets

/**
 * Diese Klasse dient dem Abschicken der Bewertung an den vorherigen Host. Dies ist erst moeglich nach der
 * ersten Rotation, sofern das Treffen auch nicht abgesagt wurde(lastHost != "" im JSON des Servers).
 * Die Logik wird grossteils in evaluation.php sichergestellt und hier interpretiert (Response).
 */
class EvaluationFragment : Fragment() {

    //Implementieren der ViewBinding-Klasse des Layouts
    private var _binding: EvaluationFragLayoutBinding? = null
    private val binding get() = _binding!!

    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var connection: DBServerConnectionClass

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = EvaluationFragLayoutBinding.inflate(inflater, container, false)

        connection = DBServerConnectionClass(requireContext())

        //Klick-Button zum Abschicken der Evaluation
        binding.buttonEvaluationCommit.setOnClickListener {
            scope.launch {
                sendEvaluation(
                    binding.ratingBarMeal.rating.toString(),
                    binding.ratingBarHost.rating.toString(),
                    binding.ratingBarEvening.rating.toString()
                )
            }
        }

        return binding.root
    }

    /**
     * Diese Methode schickt das Rating an den Server (evaluation.php)
     * @param evalEvening Rating des Abends (0-5)
     * @param evalHost Rating des Gastgebers (0-5)
     * @param evalMeal Rating des Essens (0-5)
     */
    private suspend fun sendEvaluation(evalMeal: String, evalHost: String, evalEvening: String) {
        var group = ""
        var user = ""
        val userGroupObject = connection.getCurrentUserGroup()
        if (userGroupObject.containsKey("user") && userGroupObject.containsKey("group")) {
            group = userGroupObject.getAsString("group")
            user = userGroupObject.getAsString("user")
        }

        val client = OkHttpClient()
        val url = "https://spieleabend.open-serv.info/evaluation.php"
        val body: FormBody = FormBody.Builder(StandardCharsets.UTF_8)
            .add("access", "spieleabend.iwbm@access")
            .add("action", "set")
            .add("group", group)
            .add("user", user)
            .add("evalMeal", evalMeal)
            .add("evalHost", evalHost)
            .add("evalEvening", evalEvening)
            .build()

        val request: Request = Request
            .Builder()
            .url(url)
            .post(body)
            .build()

        val response = if (group.isNotEmpty()) {
            try {
                client.newCall(request).execute().body!!.string()
            } catch (error: IOException) {
                Log.e("Error in EvalFrag", "Error: $error")
                "Fehler: Server"
            } catch (error: Exception) {
                Log.e("Error in EvalFrag", "Error: $error")
                "Fehler: Unbekannt"
            }
        } else {
            "Fehler: No Group"
        }

        withContext(Dispatchers.Main) {
            if (response == "OK") {
                Toast.makeText(requireContext(), response, Toast.LENGTH_SHORT).show()
            } else {
                //Interpretieren der moeglichen technischen und logischen Fehlersignale, die als Response vom evaluation.php gesendet wurden
                //Feedback dabei als Toast dargestellt.
                Toast.makeText(
                    requireContext(),
                    when (response) {
                        "Group not found" -> getString(R.string.group_not_found)
                        "Can't vote twice!" -> getString(R.string.eval_double)
                        "Game night isn't over!" -> getString(R.string.eval_early)
                        "You can't vote yourself!" -> getString(R.string.eval_yourself)
                        "Server" -> getString(R.string.server_not_found)
                        "Fehler: No Group" -> getString(R.string.no_group_found)
                        else -> getString(R.string.unknown_error)
                    }, Toast.LENGTH_SHORT
                ).show()
            }
            println(response)
            binding.ratingBarMeal.rating = 0F
            binding.ratingBarHost.rating = 0F
            binding.ratingBarEvening.rating = 0F
        }
    }
}