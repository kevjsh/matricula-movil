package com.example.matricula.view

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AppCompatActivity
import com.example.matricula.BuildConfig
import com.example.matricula.R
import com.example.matricula.databinding.ActivityUsersBinding
import com.example.matricula.model.Career
import com.example.matricula.model.User
import com.example.matricula.service.UsersService
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

class UsersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUsersBinding

    private lateinit var allCareers: MutableList<Career>
    private var selectedUser: User? = null
    private var selectedCareer: Career? = null

    private var userType: String? = null
    private var selectedDate: String? = null

    private val userTypes = arrayOf("Administrador", "Matriculador", "Profesor", "Alumno")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user: User = Gson().fromJson(intent.getStringExtra("User"), User::class.java)
        userType = intent.getStringExtra("Usertype")
        binding.usertype.text = userType


        val saveUser = binding.saveUser

        // Using coroutines to resolve data requirements
        CoroutineScope(Dispatchers.IO).launch {

            getAllCareers()

            withContext(Dispatchers.Main) {
                getAllUsers()
            }
        }

        saveUser.setOnClickListener {
            selectedUser = null
            saveUserDialog()
        }

    }

    /* Basic operations */
    private fun saveUser(user: User) {

        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).build()
        val service = retrofit.create(UsersService::class.java)

        val jsonObject = JSONObject()

        // Here we can create the body of http request
        jsonObject.put("id", user.id)
        jsonObject.put("personId", user.personId)
        jsonObject.put("name", user.name)
        jsonObject.put("telephone", user.telephone)
        jsonObject.put("birthday", user.birthday)
        jsonObject.put("careerId", user.careerId)
        jsonObject.put("roleId", user.roleId)
        jsonObject.put("email", user.email)
        jsonObject.put("password", user.password)

        // Type configurations
        val jsonObjectString = jsonObject.toString()
        val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

        // Implements coroutines
        CoroutineScope(Dispatchers.IO).launch {

            val response = service.saveUser(requestBody)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    message("Actualización correcta!")

                } else {
                    message("Ocurrió un error en la actualización!")
                }
            }

        }

    }

    private fun deleteUser(id: Int) {

        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).build()
        val service = retrofit.create(UsersService::class.java)

        // Implements coroutines
        CoroutineScope(Dispatchers.IO).launch {

            val response = service.deleteUser(id)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    message("Usuario eliminado correctamnte!")
                } else {
                    message("Ocurrió un error en la actualización!")
                }
            }

        }
    }

    // Function to display the custom dialog.
    private fun saveUserDialog() {

        // Dialog configurations
        val dialog = Dialog(this@UsersActivity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.saveuser_dialog)
        val lp = WindowManager.LayoutParams()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;

        // Dialog elements
        val personId: EditText = dialog.findViewById(R.id.personId)
        val name: EditText = dialog.findViewById(R.id.name)
        val telephone: EditText = dialog.findViewById(R.id.telephone)
        val birthday: EditText = dialog.findViewById(R.id.birthday)
        val role: Spinner = dialog.findViewById(R.id.roles)
        val email: EditText = dialog.findViewById(R.id.email)
        val careerName: AutoCompleteTextView = dialog.findViewById(R.id.career)
        val password: EditText = dialog.findViewById(R.id.password)

        // Data for autocomplete text view
        var careerNames: Array<String> = allCareers.map { c -> c.name }.toList().toTypedArray();
        val careersAdapter: ArrayAdapter<String> =
            ArrayAdapter<String>(this, android.R.layout.select_dialog_item, careerNames)
        careerName.threshold = 1
        careerName.setAdapter(careersAdapter)

        careerName.onItemClickListener = OnItemClickListener { _, _, i, _ ->
            selectedCareer = allCareers.first { c -> c.name == careersAdapter.getItem(i).toString() }
        }

        val rolesAdapter: ArrayAdapter<String> =
            ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, userTypes)
        role.adapter = rolesAdapter


        // To manage birthday day
        birthday.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker().build()
            datePicker.show(supportFragmentManager, "DatePicker")

            datePicker.addOnPositiveButtonClickListener {
                val dateFormatter = SimpleDateFormat("yyyy-MM-dd")
                selectedDate = dateFormatter.format(Date(it))
                birthday.text = Editable.Factory.getInstance().newEditable(selectedDate.toString())
            }
        }

        //Initializing the fields of the dialog
        if (userType != "Alumnos") {
            careerName.visibility = View.GONE
        }

        if (selectedUser != null) {

            personId.text = Editable.Factory.getInstance().newEditable(selectedUser?.personId)
            name.text = Editable.Factory.getInstance().newEditable(selectedUser?.name)
            telephone.text = Editable.Factory.getInstance().newEditable(selectedUser?.telephone.toString())

            birthday.text = Editable.Factory.getInstance().newEditable(selectedUser?.birthday.toString())
            val dateFormatter = SimpleDateFormat("yyyy-MM-dd")
            selectedDate = dateFormatter.format(selectedUser?.birthday)

            role.setSelection(selectedUser?.roleId!! - 1)
            email.text = Editable.Factory.getInstance().newEditable(selectedUser?.email)

            if (userType == "Alumnos") {
                careerName.text = Editable.Factory.getInstance().newEditable(allCareers.first { c -> c.id == selectedUser?.careerId }.name)
            }

        }

        // Buttons
        val saveUser: Button = dialog.findViewById(R.id.saveUser)
        val deleteUser: Button = dialog.findViewById(R.id.deleteUser)

        // Listeners
        saveUser.setOnClickListener {

            var id = 0
            var careerId = 0

            if (selectedUser != null) {
                id = selectedUser?.id!!
            }

            if (selectedCareer != null) {
                careerId = selectedCareer?.id!!
            }

            selectedUser = User(
                id,
                personId.text.toString(),
                name.text.toString(),
                telephone.text.toString().toInt(),
                Date.valueOf(selectedDate),
                careerId,
                role.selectedItemPosition + 1,
                email.text.toString(),
                password.text.toString(),
            )

            saveUser(selectedUser!!)
            dialog.dismiss()
        }

        deleteUser.setOnClickListener {
            if (selectedUser != null) {
                deleteUser(selectedUser!!.id)
            }
            dialog.dismiss()
        }

        // Showing dialog
        dialog.show()
        dialog.window?.attributes = lp;
    }

    /* Update view */
    private fun updateTable(usersList: MutableList<User>) {

        var table = binding.table

        try {

            // Cleaning the table
            this@UsersActivity.runOnUiThread(java.lang.Runnable {
                table.removeAllViews()
            })

            // Generating Headers
            val header = TableRow(this)
            header.setBackgroundColor(Color.GRAY)

            val c1 = TextView(this)
            val c2 = TextView(this)
            val c3 = TextView(this)
            val c4 = TextView(this)

            c1.text = "Identificación"
            c2.text = "Nombre"
            c3.text = "Teléfono"
            //c4.text = "Fecha Nacimiento"
            c4.text = "Email"

            header.addView(c1);
            header.addView(c2);
            header.addView(c3);
            header.addView(c4);

            // Student case
            if (userType == "Alumnos") {
                val c5 = TextView(this)
                c5.text = "Carrera"
                header.addView(c5);
            }


            this@UsersActivity.runOnUiThread(java.lang.Runnable {
                table.addView(header)
            })

            // Each course
            for (user in usersList) {

                // Elements
                val row = TableRow(this)

                val personId = TextView(this)
                val name = TextView(this)
                val telephone = TextView(this)
                //val birthday = TextView(this)
                val email = TextView(this)

                // Fill elements
                personId.text = user.personId
                name.text = user.name
                telephone.text = user.telephone.toString()
                email.text = user.email


                row.addView(personId);
                row.addView(name);
                row.addView(telephone);
                row.addView(email);

                // Student case
                if (userType == "Alumnos") {
                    val career = TextView(this)
                    career.text = allCareers.first { c -> c.id == user.careerId }.name
                    row.addView(career);
                }


                this@UsersActivity.runOnUiThread(java.lang.Runnable {
                    table.addView(row)
                })

                row.setOnClickListener {
                    selectedUser = user
                    saveUserDialog()
                }
            }

        } catch (ex: Exception) {
            message(ex.message.toString())
        }

    }

    /* Get data from Websockets */
    private fun getAllUsers() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/users").build()

        httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, usersWS: String) {
                super.onMessage(webSocket, usersWS)

                // Parsing data
                val users = Gson().fromJson(usersWS, MutableList::class.java);
                val usersList: MutableList<User> = mutableListOf()

                for (user in users) {
                    usersList.add(Gson().fromJson(Gson().toJson(user), User::class.java))
                }

                if (userType == "Profesores") {
                    updateTable(usersList.filter { u -> u.roleId == 3 }.toMutableList())
                } else if (userType == "Alumnos") {
                    updateTable(usersList.filter { u -> u.roleId == 4 }.toMutableList())
                } else {
                    updateTable(usersList.filter { u -> u.roleId == 1 || u.roleId == 2 }.toMutableList())
                }

            }
        })
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

                for (course in careers) {
                    careersList.add(Gson().fromJson(Gson().toJson(course), Career::class.java))
                }

                // Update the information
                allCareers = careersList;

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