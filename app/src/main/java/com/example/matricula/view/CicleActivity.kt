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
import com.example.matricula.databinding.ActivityCicleBinding
import com.example.matricula.model.Cicle
import com.example.matricula.model.User
import com.example.matricula.service.CicleService
import com.google.android.material.datepicker.MaterialDatePicker
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
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

class CicleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCicleBinding
    private lateinit var allCicles: MutableList<Cicle>
    private var selectedCicle: Cicle? = null
    private var selectedInitDate: String? = null
    private var selectedFinishDate: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCicleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user: User = Gson().fromJson(intent.getStringExtra("User"), User::class.java)
        val saveCicle = binding.saveCicle

        CoroutineScope(Dispatchers.IO).launch {
            getAllCicles()
        }

        saveCicle.setOnClickListener {
            selectedCicle = null
            saveCicleDialog()
        }
    }

    private fun saveCicle(cicle: Cicle) {

        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).build()
        val service = retrofit.create(CicleService::class.java)

        val jsonObject = JSONObject()

        // Here we can create the body of http request
        jsonObject.put("id", cicle.id)
        jsonObject.put("year", cicle.year)
        jsonObject.put("cicleNumber", cicle.cicleNumber)
        jsonObject.put("initDate", cicle.initDate)
        jsonObject.put("finishDate", cicle.finishDate)

        // Type configurations
        val jsonObjectString = jsonObject.toString()
        val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

        // Implements coroutines
        CoroutineScope(Dispatchers.IO).launch {

            val response = service.saveCicle(requestBody)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    message("Actualización correcta!")
                } else {
                    message("Ocurrió un error en la actualización!")
                }
            }
        }
    }

    private fun deleteCicle(id: Int) {

        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).build()
        val service = retrofit.create(CicleService::class.java)

        // Implements coroutines
        CoroutineScope(Dispatchers.IO).launch {

            val response = service.deleteCicle(id)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    message("Carrera eliminada correctamente!")
                } else {
                    message("Ocurrió un error en la actualización!")
                }
            }

        }
    }

    private fun saveCicleDialog() {

        // Dialog configurations
        val dialog = Dialog(this@CicleActivity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.savecicle_dialog)
        val lp = WindowManager.LayoutParams()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;

        // Dialog elements
        val year: EditText = dialog.findViewById(R.id.year_cicle)
        val cicleNumber: EditText = dialog.findViewById(R.id.ciclenumber)
        val initDate: EditText = dialog.findViewById(R.id.initDate)
        val finishDAte: EditText = dialog.findViewById(R.id.finishDAte)

        // To manage init day
        initDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker().build()
            datePicker.show(supportFragmentManager, "DatePicker")

            datePicker.addOnPositiveButtonClickListener {
                val dateFormatter = SimpleDateFormat("yyyy-MM-dd")
                selectedInitDate = dateFormatter.format(Date(it))
                initDate.text = Editable.Factory.getInstance().newEditable(selectedInitDate.toString())
            }
        }

        // To manage finish day
        finishDAte.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker().build()
            datePicker.show(supportFragmentManager, "DatePicker")

            datePicker.addOnPositiveButtonClickListener {
                val dateFormatter = SimpleDateFormat("yyyy-MM-dd")
                selectedFinishDate = dateFormatter.format(Date(it))
                finishDAte.text = Editable.Factory.getInstance().newEditable(selectedFinishDate.toString())
            }
        }


        //Initializing the fields of the dialog
        if (selectedCicle != null) {

            year.text = Editable.Factory.getInstance().newEditable(selectedCicle?.year.toString())
            cicleNumber.text = Editable.Factory.getInstance().newEditable(selectedCicle?.cicleNumber.toString())
            initDate.text = Editable.Factory.getInstance().newEditable(selectedCicle?.initDate.toString())
            val dateFormatter = SimpleDateFormat("yyyy-MM-dd")
            selectedInitDate = dateFormatter.format(selectedCicle?.initDate)
            finishDAte.text = Editable.Factory.getInstance().newEditable(selectedCicle?.finishDate.toString())
            selectedFinishDate = dateFormatter.format(selectedCicle?.finishDate)
        }

        // Buttons
        val saveCicle: Button = dialog.findViewById(R.id.saveCicle)
        val deleteCicle: Button = dialog.findViewById(R.id.deleteCicle)

        // Listeners
        saveCicle.setOnClickListener {

            var id = 0

            if (selectedCicle != null) {
                id = selectedCicle?.id!!
            }
            selectedCicle = Cicle(
                id,
                year.text.toString().toInt(),
                cicleNumber.text.toString().toInt(),
                Date.valueOf(selectedInitDate),
                Date.valueOf(selectedFinishDate)
            )

            saveCicle(selectedCicle!!)


            dialog.dismiss()
        }

        deleteCicle.setOnClickListener {
            if (selectedCicle != null) {
                deleteCicle(selectedCicle!!.id)
            }
            dialog.dismiss()
        }

        // Showing dialog
        dialog.show()
        dialog.window?.attributes = lp;
    }

    private fun updateTable(ciclesList: MutableList<Cicle>) {

        var table = binding.tableCicles

        try {

            // Cleaning the table
            this@CicleActivity.runOnUiThread(java.lang.Runnable {
                table.removeAllViews()
            })

            // Generating Headers
            val header = TableRow(this)
            header.setBackgroundColor(Color.GRAY)

            val c1 = TextView(this)
            val c2 = TextView(this)
            val c3 = TextView(this)
            val c4 = TextView(this)

            c1.text = "Año"
            c2.text = "Numero de ciclo"
            c3.text = "Fecha de inicio"
            c4.text = "Fecha de finalización"

            header.addView(c1)
            header.addView(c2)
            header.addView(c3)
            header.addView(c4)

            this@CicleActivity.runOnUiThread(java.lang.Runnable {
                table.addView(header)
            })

            // Each course
            for (cicle in ciclesList) {

                // Elements
                val row = TableRow(this)

                val year = TextView(this)
                val ciclenumber = TextView(this)
                val initDate = TextView(this)
                val finishDate = TextView(this)

                // Fill elements
                year.text = cicle.year.toString()
                ciclenumber.text = cicle.cicleNumber.toString()
                initDate.text = cicle.initDate.toString()
                finishDate.text = cicle.finishDate.toString()


                row.addView(year);
                row.addView(ciclenumber);
                row.addView(initDate);
                row.addView(finishDate);

                this@CicleActivity.runOnUiThread(java.lang.Runnable {
                    table.addView(row)
                })

                row.setOnClickListener {
                    selectedCicle = cicle
                    saveCicleDialog()
                }
            }

        } catch (ex: Exception) {
            message(ex.message.toString())
        }

    }

    private fun getAllCicles() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/cicles").build()

        httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, ciclesWS: String) {
                super.onMessage(webSocket, ciclesWS)

                // Parsing data
                val cicles = Gson().fromJson(ciclesWS, MutableList::class.java);
                val ciclesList: MutableList<Cicle> = mutableListOf()

                for (cicle in cicles) {
                    ciclesList.add(Gson().fromJson(Gson().toJson(cicle), Cicle::class.java))
                }

                // Update the information
                updateTable(ciclesList)
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