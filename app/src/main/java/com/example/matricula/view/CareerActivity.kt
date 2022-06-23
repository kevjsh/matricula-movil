package com.example.matricula.view

import android.app.Dialog
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.view.Window
import android.view.WindowManager
import android.widget.*
import com.example.matricula.BuildConfig
import com.example.matricula.R
import com.example.matricula.databinding.ActivityCareerBinding
import com.example.matricula.databinding.ActivityCoursesBinding
import com.example.matricula.model.Career
import com.example.matricula.model.Course
import com.example.matricula.model.User
import com.example.matricula.service.CareerService
import com.example.matricula.service.CoursesService
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class CareerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCareerBinding
    private lateinit var allCareers: MutableList<Career>
    private var selectedCareer: Career? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCareerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user: User = Gson().fromJson(intent.getStringExtra("User"), User::class.java)
        val saveCareer = binding.saveCareer

        CoroutineScope(Dispatchers.IO).launch {
            getAllCareers()
        }

        saveCareer.setOnClickListener {
            selectedCareer = null
            saveCareerDialog()
        }
    }

    private fun saveCareer(career: Career) {

        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).build()
        val service = retrofit.create(CareerService::class.java)

        val jsonObject = JSONObject()

        // Here we can create the body of http request
        jsonObject.put("id", career.id)
        jsonObject.put("code", career.code)
        jsonObject.put("name", career.name)
        jsonObject.put("title", career.title)

        // Type configurations
        val jsonObjectString = jsonObject.toString()
        val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

        // Implements coroutines
        CoroutineScope(Dispatchers.IO).launch {

            val response = service.saveCareer(requestBody)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    message("Actualización correcta!")

                } else {
                    message("Ocurrió un error en la actualización!")
                }
            }
        }
    }

    private fun deleteCareer(id: Int) {

        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).build()
        val service = retrofit.create(CareerService::class.java)

        // Implements coroutines
        CoroutineScope(Dispatchers.IO).launch {

            val response = service.deleteCareer(id)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    message("Carrera eliminada correctamente!")
                } else {
                    message("Ocurrió un error en la actualización!")
                }
            }

        }
    }

    private fun saveCareerDialog() {

        // Dialog configurations
        val dialog = Dialog(this@CareerActivity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.savecareer_dialog)
        val lp = WindowManager.LayoutParams()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;

        // Dialog elements
        val code: EditText = dialog.findViewById(R.id.code_career)
        val name: EditText = dialog.findViewById(R.id.name_career)
        val title: EditText = dialog.findViewById(R.id.title_career)


        //Initializing the fields of the dialog
        if (selectedCareer != null) {

            code.text = Editable.Factory.getInstance().newEditable(selectedCareer?.code)
            name.text = Editable.Factory.getInstance().newEditable(selectedCareer?.name)
            title.text = Editable.Factory.getInstance().newEditable(selectedCareer?.title)
        }

        // Buttons
        val saveCareer: Button = dialog.findViewById(R.id.saveCareer)
        val deleteCareer: Button = dialog.findViewById(R.id.deleteCareer)

        // Listeners
        saveCareer.setOnClickListener {

            var id = 0

            if (selectedCareer != null) {
                id = selectedCareer?.id!!
            }
            selectedCareer = Career(
                id,
                code.text.toString(),
                name.text.toString(),
                title.text.toString()
            )

            saveCareer(selectedCareer!!)


            dialog.dismiss()
        }

        deleteCareer.setOnClickListener {
            if (selectedCareer != null) {
                deleteCareer(selectedCareer!!.id)
            }
            dialog.dismiss()
        }

        // Showing dialog
        dialog.show()
        dialog.window?.attributes = lp;
    }

    private fun updateTable(careerList: MutableList<Career>) {

        var table = binding.tableCareers

        try {
            // Cleaning the table
            this@CareerActivity.runOnUiThread(java.lang.Runnable {
                table.removeAllViews()
            })

            // Generating Headers
            val header = TableRow(this)
            header.setBackgroundColor(Color.GRAY)

            val c1 = TextView(this)
            val c2 = TextView(this)
            val c3 = TextView(this)

            c1.text = "Código"
            c2.text = "Nombre"
            c3.text = "Titulo"

            header.addView(c1);
            header.addView(c2);
            header.addView(c3);

            this@CareerActivity.runOnUiThread(java.lang.Runnable {
                table.addView(header)
            })

            // Each course
            for (career in careerList) {

                // Elements
                val row = TableRow(this)

                val code = TextView(this)
                val name = TextView(this)
                val title = TextView(this)

                // Fill elements
                code.text = career.code
                name.text = career.name
                title.text = career.title

                row.addView(code);
                row.addView(name);
                row.addView(title);

                this@CareerActivity.runOnUiThread(java.lang.Runnable {
                    table.addView(row)
                })

                row.setOnClickListener {
                    selectedCareer = career
                    saveCareerDialog()
                }
            }
        } catch (ex: Exception) {
            message(ex.message.toString())
        }
    }

    private fun getAllCareers() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/careers").build()

        httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, careersWS: String) {
                super.onMessage(webSocket, careersWS)

                // Parsing data
                val careers = Gson().fromJson(careersWS, MutableList::class.java);
                val careersList: MutableList<Career> = mutableListOf()

                for (career in careers) {
                    careersList.add(Gson().fromJson(Gson().toJson(career), Career::class.java))
                }

                // Update the information
                updateTable(careersList)
            }
        })
    }

    /* Fast message on screen */
    private fun message(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}