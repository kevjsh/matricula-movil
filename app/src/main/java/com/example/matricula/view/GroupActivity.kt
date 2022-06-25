package com.example.matricula.view

import android.R
import android.app.Dialog
import android.widget.AdapterView.OnItemClickListener
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import com.example.matricula.BuildConfig
import com.example.matricula.databinding.ActivityGroupBinding
import com.example.matricula.model.*
import com.example.matricula.service.GroupsService
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
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class GroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupBinding

    private lateinit var allProfessors: MutableList<User>
    private lateinit var allCareers: MutableList<Career>
    private lateinit var allCicles: MutableList<Cicle>
    private lateinit var allCourses: MutableList<Course>

    private var selectedGroup: Group? = null
    private var selectedProfessor: User? = null
    private var selectedCareer: Career? = null
    private var selectedCicle: Int? = null
    private var selectedCourse: Course? = null

    // Websockets
    private lateinit var groupsSocket: WebSocket


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        var ab = supportActionBar
        if (ab!=null) {
            ab.title = "Matrícula"
        }

        getAllProfessors();

        binding.saveGroup.setOnClickListener{
            selectedGroup = null
            saveGroupDialog()
        }
    }

    /* Basic operations */
    private fun saveGroup(group: Group) {

        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).build()
        val service = retrofit.create(GroupsService::class.java)

        // Type configurations
        val jsonObjectString = Gson().toJson(group)

        val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

        // Implements coroutines
        CoroutineScope(Dispatchers.IO).launch {

            val response = service.saveGroup(requestBody)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    message("Actualización correcta!")

                } else {
                    message("Ocurrió un error en la actualización!")
                }
            }

        }

    }

    private fun deleteGroup(id: Int) {

        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).build()
        val service = retrofit.create(GroupsService::class.java)

        // Implements coroutines
        CoroutineScope(Dispatchers.IO).launch {

            val response = service.deleteGroup(id)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    message("Grupo eliminado correctamente!")
                } else {
                    message("Ocurrió un error en la actualización!")
                }
            }

        }
    }

    private fun initializations() {

        // Data for autocomplete text view careers
        var careerNames: Array<String> = allCareers.map { c -> c.name }.toList().toTypedArray();
        val careerAdapter: ArrayAdapter<String> = ArrayAdapter<String>(this, R.layout.select_dialog_item, careerNames)

        // Data for cicles
        var ciclesNames: Array<String> = allCicles.map { c -> "${c.year} - ${c.cicleNumber}" }.toList().toTypedArray();
        val ciclesAdapter: ArrayAdapter<String> =
            ArrayAdapter<String>(this, R.layout.simple_spinner_dropdown_item, ciclesNames)

        // Data for autocomplete text view courses
        var courseNames: Array<String> = arrayOf()
        var courseAdapter: ArrayAdapter<String> = ArrayAdapter<String>(this, R.layout.select_dialog_item, courseNames)

        this@GroupActivity.runOnUiThread(java.lang.Runnable {

            // Careers
            binding.careers.threshold = 1
            binding.careers.setAdapter(careerAdapter)

            // Cicles
            binding.cicles.adapter = ciclesAdapter

            binding.cicles.visibility = View.GONE
            binding.courses.visibility = View.GONE
            binding.enrollmentLayout.visibility = View.GONE

            binding.careers.onItemClickListener = OnItemClickListener { _, _, i, _ ->
                selectedCareer = allCareers.first { c -> c.name == careerAdapter.getItem(i).toString() }
                binding.cicles.visibility = View.VISIBLE

                // Data for autocomplete text view courses
                courseNames = allCourses.filter { c -> c.careerId == selectedCareer?.id }.map { c -> c.name }.toList().toTypedArray();
                courseAdapter = ArrayAdapter<String>(this, R.layout.select_dialog_item, courseNames)
                binding.courses.threshold = 1
                binding.courses.setAdapter(courseAdapter)
            }

            binding.cicles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                    binding.courses.visibility = View.VISIBLE
                    selectedCicle = binding.cicles.selectedItemPosition + 1
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

            binding.courses.onItemClickListener = OnItemClickListener { _, _, i, _ ->
                selectedCourse = allCourses.first { c -> c.name == courseAdapter.getItem(i).toString() }
                binding.enrollmentLayout.visibility = View.VISIBLE

                if (::groupsSocket.isInitialized) {
                    groupsSocket.cancel()
                }
                getAllGroups()
            }

        })

    }


    /* Function to display the custom dialog. */
    private fun saveGroupDialog(){

        // Dialog configurations
        val dialog = Dialog(this@GroupActivity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(com.example.matricula.R.layout.savegroup_dialog)
        val lp = WindowManager.LayoutParams()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;

        // Dialog elements
        val courseName: AutoCompleteTextView = dialog.findViewById(com.example.matricula.R.id.course)
        val schedule: EditText = dialog.findViewById(com.example.matricula.R.id.schedule)
        val professorName: AutoCompleteTextView = dialog.findViewById(com.example.matricula.R.id.professor)

        // Data for autocomplete text view courses
        var coursesNames: Array<String> = allCourses.filter { c -> c.careerId == selectedCareer?.id }.map { c -> c.name }.toList().toTypedArray();
        val coursesAdapter: ArrayAdapter<String> = ArrayAdapter<String>(this, R.layout.select_dialog_item, coursesNames)
        courseName.threshold = 1
        courseName.setAdapter(coursesAdapter)

        // Data for autocomplete text view professors
        var professorsNames: Array<String> = allProfessors.map { u -> u.name }.toList().toTypedArray();
        val professorsAdapter: ArrayAdapter<String> = ArrayAdapter<String>(this, R.layout.select_dialog_item, professorsNames)
        professorName.threshold = 1
        professorName.setAdapter(professorsAdapter)

        // Listeners for autocomplete text views
        courseName.onItemClickListener = OnItemClickListener { _, _, i, _ ->
            selectedCourse = allCourses.first { c -> c.name == coursesAdapter.getItem(i).toString() }
        }

        professorName.onItemClickListener = OnItemClickListener { _, _, i, _ ->
            selectedProfessor = allProfessors.first { u -> u.name == professorsAdapter.getItem(i).toString() }
        }

        if (selectedGroup != null) {
            courseName.text = Editable.Factory.getInstance().newEditable(allCourses.first { c -> c.id == selectedGroup?.courseId }.name)
            schedule.text = Editable.Factory.getInstance().newEditable(selectedGroup?.schedule)
            professorName.text = Editable.Factory.getInstance().newEditable(allProfessors.first { u -> u.id == selectedGroup?.professorId }.name)
        }

        // Buttons
        val saveUser: Button = dialog.findViewById(com.example.matricula.R.id.saveGroup)
        val deleteUser: Button = dialog.findViewById(com.example.matricula.R.id.deleteGroup)

        // Listeners
        saveUser.setOnClickListener {

            var id = 0
            var groupNumber = 0

            if (selectedGroup != null) {
                id = selectedGroup?.id!!
                groupNumber = selectedGroup?.groupNumber!!
            }

            selectedGroup = Group(
                id,
                selectedCourse?.id!!,
                groupNumber,
                schedule.text.toString(),
                selectedCicle!!,
                selectedProfessor?.id!!
            )

            saveGroup(selectedGroup!!)
            dialog.dismiss()
        }

        deleteUser.setOnClickListener {
            if (selectedGroup != null) {
                deleteGroup(selectedGroup!!.id)
            }
            dialog.dismiss()
        }

        // Showing dialog
        dialog.show()
        dialog.window?.attributes = lp;

    }

    /* Update view */
    private fun updateTable(groupList: MutableList<Group>) {

        var table = binding.table

        try {

            // Cleaning the table
            this@GroupActivity.runOnUiThread(java.lang.Runnable {
                table.removeAllViews()
            })

            // Generating Headers
            val header = TableRow(this)
            header.setBackgroundColor(Color.GRAY)

            val c1 = TextView(this)
            val c2 = TextView(this)
            val c3 = TextView(this)
            val c4 = TextView(this)

            c1.text = "Curso"
            c2.text = "Grupo"
            c3.text = "Horario"
            c4.text = "Profesor"

            header.addView(c1);
            header.addView(c2);
            header.addView(c3);
            header.addView(c4);

            this@GroupActivity.runOnUiThread(java.lang.Runnable {
                table.addView(header)
            })

            // Each course
            for (group in groupList) {

                // Elements
                val row = TableRow(this)

                val course = TextView(this)
                val groupNumber = TextView(this)
                val schedule = TextView(this)
                val professor = TextView(this)

                // Fill elements
                course.text = allCourses.first { c -> c.id == group.courseId }.name
                groupNumber.text = group.groupNumber.toString()
                schedule.text = group.schedule
                professor.text = allProfessors.first { u -> u.id == group.professorId }.name

                row.addView(course);
                row.addView(groupNumber);
                row.addView(schedule);
                row.addView(professor);

                this@GroupActivity.runOnUiThread(java.lang.Runnable {
                    table.addView(row)
                })

                row.setOnClickListener {
                    selectedGroup = group
                    saveGroupDialog()
                }

            }

        } catch (ex: Exception) {
            message(ex.message.toString())
        }

    }

    /* Get data from Websockets */

    private fun getAllProfessors() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/users").build()

        httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, usersWS: String) {
                super.onMessage(webSocket, usersWS)

                // Parsing data
                val users = Gson().fromJson(usersWS, MutableList::class.java);
                var professorList: MutableList<User> = mutableListOf()

                for (student in users) {
                    professorList.add(Gson().fromJson(Gson().toJson(student), User::class.java))
                }

                professorList = professorList.filter { u -> u.roleId == 3 }.toMutableList()

                // Update the information
                allProfessors = professorList;

                getAllCareers()

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

                getAllCicles()

            }
        })
    }

    private fun getAllCicles() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/cicles").build()

        httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, ciclesWS: String) {
                super.onMessage(webSocket, ciclesWS)

                // Parsing data
                val cicles = Gson().fromJson(ciclesWS, MutableList::class.java);
                var cicleList: MutableList<Cicle> = mutableListOf()

                for (cicle in cicles) {
                    cicleList.add(Gson().fromJson(Gson().toJson(cicle), Cicle::class.java))
                }

                // Update the information
                allCicles = cicleList

                getAllCourses()

            }
        })
    }

    private fun getAllCourses() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/courses").build()

        httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, coursesWS: String) {
                super.onMessage(webSocket, coursesWS)

                // Parsing data
                val courses = Gson().fromJson(coursesWS, MutableList::class.java);
                val coursesList: MutableList<Course> = mutableListOf()

                for (course in courses) {
                    coursesList.add(Gson().fromJson(Gson().toJson(course), Course::class.java))
                }

                // Update the information
                allCourses = coursesList

                try {
                    initializations()
                } catch (ex: Exception) {
                    message(ex.message.toString())
                }

            }
        })
    }

    private fun getAllGroups() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/groups").build()

        groupsSocket = httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, groupsWS: String) {
                super.onMessage(webSocket, groupsWS)

                // Parsing data
                val groups = Gson().fromJson(groupsWS, MutableList::class.java);
                var groupsList: MutableList<Group> = mutableListOf()

                for (group in groups) {
                    groupsList.add(Gson().fromJson(Gson().toJson(group), Group::class.java))
                }

                groupsList = groupsList.filter { g -> g.cicleId == selectedCicle && g.courseId == selectedCourse?.id }.toMutableList()

                // Update the information
                updateTable(groupsList)
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