package com.example.matricula.view

import android.R
import android.app.Dialog
import android.graphics.Color
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemSelectedListener
import androidx.annotation.RequiresApi
import com.example.matricula.BuildConfig
import com.example.matricula.databinding.ActivityEnrollmentBinding
import com.example.matricula.model.*
import com.example.matricula.service.EnrollmentsService
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
import okhttp3.internal.wait
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class EnrollmentActivity : AppCompatActivity() {

    private lateinit var user: User
    private lateinit var binding: ActivityEnrollmentBinding
    private var menuType: String? = null
    private lateinit var allStudents: MutableList<User>
    private lateinit var allCicles: MutableList<Cicle>
    private lateinit var allCourses: MutableList<Course>
    private lateinit var allGroups: MutableList<Group>

    private var selectedStudent: User? = null
    private var selectedCicle: Int? = null
    private var selectedEnrollment: Enrollment? = null
    private var selectedCourse: Course? = null

    // Websockets
    private lateinit var enrollmentsSocket: WebSocket


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnrollmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        user = Gson().fromJson(intent.getStringExtra("User"), User::class.java)
        menuType = intent.getStringExtra("Menutype")
        binding.menutype.text = menuType

        setSupportActionBar(binding.toolbar)
        var ab = supportActionBar
        if (ab!=null) {
            ab.title = "Matrícula"
        }

        getAllStudents()

        binding.saveEnrollment.setOnClickListener {
            selectedEnrollment = null
            saveEnrollmentDialog()
        }

        if (user.roleId == 4) {
            binding.saveEnrollment.visibility = View.GONE
            selectedStudent = user
            binding.students.text = Editable.Factory.getInstance().newEditable(user.name)
            binding.students.focusable = View.NOT_FOCUSABLE
        }

        if(menuType == "Historial"){
            binding.saveEnrollment.visibility = View.GONE
        }

    }

    /* Basic operations */
    private fun saveEnrollment(enrollment: Enrollment) {

        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).build()
        val service = retrofit.create(EnrollmentsService::class.java)

        // Type configurations
        enrollment.user.birthday = null
        val jsonObjectString = Gson().toJson(enrollment)

        val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

        // Implements coroutines
        CoroutineScope(Dispatchers.IO).launch {

            val response = service.saveEnrollment(requestBody)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    message("Actualización correcta!")

                } else {
                    message("Ocurrió un error en la actualización!")
                }
            }

        }

    }

    private fun deleteEnrollment(id: Int) {

        val retrofit = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).build()
        val service = retrofit.create(EnrollmentsService::class.java)

        // Implements coroutines
        CoroutineScope(Dispatchers.IO).launch {

            val response = service.deleteEnrollment(id)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    message("Matrícula eliminada correctamente!")
                } else {
                    message("Ocurrió un error en la actualización!")
                }
            }

        }
    }

    private fun initializations() {

        var studentNames: Array<String>

        if (user.roleId != 4) {
            studentNames = allStudents.map { u -> u.name }.toList().toTypedArray();
        } else {
            studentNames = arrayOf(user.name)
        }

        // Data for autocomplete text view
        val studentAdapter: ArrayAdapter<String> = ArrayAdapter<String>(this, R.layout.select_dialog_item, studentNames)

        // Data for cicles
        var ciclesNames: Array<String> = allCicles.map { c -> "${c.year} - ${c.cicleNumber}" }.toList().toTypedArray();
        val ciclesAdapter: ArrayAdapter<String> =
            ArrayAdapter<String>(this, R.layout.simple_spinner_dropdown_item, ciclesNames)


        this@EnrollmentActivity.runOnUiThread(java.lang.Runnable {

            binding.students.threshold = 1
            binding.students.setAdapter(studentAdapter)

            binding.cicles.adapter = ciclesAdapter

            binding.cicles.visibility = View.GONE
            binding.enrollmentLayout.visibility = View.GONE

            if (user.roleId != 4) {
                binding.students.onItemClickListener = OnItemClickListener { _, _, i, _ ->
                    selectedStudent = allStudents.first { c -> c.name == studentAdapter.getItem(i).toString() }
                    binding.cicles.visibility = View.VISIBLE
                    binding.enrollmentLayout.visibility = View.VISIBLE

                }
            } else {
                selectedStudent = user
                binding.cicles.visibility = View.VISIBLE
                binding.enrollmentLayout.visibility = View.VISIBLE
            }

            binding.cicles.onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                    selectedCicle = binding.cicles.selectedItemPosition + 1

                    if (::enrollmentsSocket.isInitialized) {
                        enrollmentsSocket.cancel()
                    }
                    getAllEnrollments()
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        })

    }

    // Function to display the custom dialog.
    private fun saveEnrollmentDialog() {

        // Dialog configurations
        val dialog = Dialog(this@EnrollmentActivity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(com.example.matricula.R.layout.saveenrollment_dialog)
        val lp = WindowManager.LayoutParams()
        lp.copyFrom(dialog.window?.attributes)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;

        // Dialog elements
        val studentName: EditText = dialog.findViewById(com.example.matricula.R.id.studentName)
        val cicleName: EditText = dialog.findViewById(com.example.matricula.R.id.cicleName)
        val courseName: AutoCompleteTextView = dialog.findViewById(com.example.matricula.R.id.course)
        val groups: Spinner = dialog.findViewById(com.example.matricula.R.id.groups)

        // Data for autocomplete text view
        var coursesNames: Array<String> =
            allCourses.filter { c -> c.careerId == selectedStudent?.careerId }.map { c -> c.name }.toList()
                .toTypedArray();
        val coursesAdapter: ArrayAdapter<String> = ArrayAdapter<String>(this, R.layout.select_dialog_item, coursesNames)
        courseName.threshold = 1
        courseName.setAdapter(coursesAdapter)

        courseName.onItemClickListener = OnItemClickListener { _, _, i, _ ->
            selectedCourse = allCourses.first { c -> c.name == coursesAdapter.getItem(i).toString() }

            var groupsNames: Array<String> =
                allGroups.filter { g -> g.cicleId == selectedCicle && g.courseId == selectedCourse?.id }
                    .map { g -> "${allCourses.first { c -> c.id == g.courseId }.name} (grupo ${g.groupNumber})" }
                    .toList().toTypedArray();

            val groupsAdapter: ArrayAdapter<String> =
                ArrayAdapter<String>(this, R.layout.simple_spinner_dropdown_item, groupsNames)
            groups.adapter = groupsAdapter
        }

        if (selectedEnrollment != null) {

            studentName.text = Editable.Factory.getInstance().newEditable(selectedEnrollment?.user?.name)
            val cicleNameEnrollment =
                "${allCicles.first { c -> c.id == selectedCicle }.year} - ${allCicles.first { c -> c.id == selectedCicle }.cicleNumber}"
            cicleName.text = Editable.Factory.getInstance().newEditable(cicleNameEnrollment)

            courseName.text = Editable.Factory.getInstance()
                .newEditable(allCourses.first { c -> c.id == selectedEnrollment?.group?.courseId }.name)

            // Data for groups
            selectedCourse = allCourses.first { c -> c.id == selectedEnrollment?.group?.courseId }

            var groupsEnrollment: Array<Group> =
                allGroups.filter { g -> g.cicleId == selectedCicle && g.courseId == selectedCourse?.id }.toList()
                    .toTypedArray();

            var groupsNames: Array<String> =
                groupsEnrollment.map { g -> "${allCourses.first { c -> c.id == g.courseId }.name} (grupo ${g.groupNumber})" }
                    .toList().toTypedArray();

            val groupsAdapter: ArrayAdapter<String> =
                ArrayAdapter<String>(this, R.layout.simple_spinner_dropdown_item, groupsNames)
            groups.adapter = groupsAdapter

            groups.setSelection(groupsEnrollment.indexOf(selectedEnrollment?.group))

        } else {
            studentName.text = Editable.Factory.getInstance().newEditable(selectedStudent?.name)
            val cicleNameEnrollment =
                "${allCicles.first { c -> c.id == selectedCicle }.year} - ${allCicles.first { c -> c.id == selectedCicle }.cicleNumber}"
            cicleName.text = Editable.Factory.getInstance().newEditable(cicleNameEnrollment)
        }

        // Buttons
        val saveEnrollment: Button = dialog.findViewById(com.example.matricula.R.id.saveEnrollment)
        val deleteEnrollment: Button = dialog.findViewById(com.example.matricula.R.id.deleteEnrollment)

        // Listeners
        saveEnrollment.setOnClickListener {

            var id = 0

            var groupsEnrollment: Array<Group> =
                allGroups.filter { g -> g.cicleId == selectedCicle && g.courseId == selectedCourse?.id }.toList()
                    .toTypedArray();

            if (selectedEnrollment != null) {
                id = selectedEnrollment?.id!!
            }

            selectedEnrollment = Enrollment(
                id,
                groupsEnrollment[groups.selectedItemPosition],
                selectedStudent!!,
                0
            )

            saveEnrollment(selectedEnrollment!!)
            dialog.dismiss()
        }

        deleteEnrollment.setOnClickListener {
            if (selectedEnrollment != null) {
                deleteEnrollment(selectedEnrollment!!.id)
            }
            dialog.dismiss()
        }

        // Showing dialog
        dialog.show()
        dialog.window?.attributes = lp;
    }

    /* Update view */
    private fun updateTable(enrollmentList: MutableList<Enrollment>) {

        var table = binding.table

        try {

            // Cleaning the table
            this@EnrollmentActivity.runOnUiThread(java.lang.Runnable {
                table.removeAllViews()
            })

            // Generating Headers
            val header = TableRow(this)
            header.setBackgroundColor(Color.GRAY)

            val c1 = TextView(this)
            val c2 = TextView(this)
            val c3 = TextView(this)

            c1.text = "Alumno"
            c2.text = "Grupo"
            c3.text = "Nota"

            header.addView(c1);
            header.addView(c2);
            header.addView(c3);

            this@EnrollmentActivity.runOnUiThread(java.lang.Runnable {
                table.addView(header)
            })

            // Each course
            for (enrollment in enrollmentList) {

                // Elements
                val row = TableRow(this)

                val studentName = TextView(this)
                val groupName = TextView(this)
                val grade = TextView(this)

                // Fill elements
                studentName.text = enrollment.user.name
                groupName.text =
                    "${allCourses.first { c -> c.id == enrollment.group.courseId }.name} (grupo ${enrollment.group.groupNumber})"
                grade.text = enrollment.grade.toString()

                row.addView(studentName);
                row.addView(groupName);
                row.addView(grade);

                this@EnrollmentActivity.runOnUiThread(java.lang.Runnable {
                    table.addView(row)
                })

                if (user.roleId != 4 && menuType != "Historial") {
                    row.setOnClickListener {
                        selectedEnrollment = enrollment
                        saveEnrollmentDialog()
                    }
                }

            }

        } catch (ex: Exception) {
            message(ex.message.toString())
        }

    }

    /* Get data from Websockets */

    private fun getAllStudents() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/users").build()

        httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, usersWS: String) {
                super.onMessage(webSocket, usersWS)

                // Parsing data
                val users = Gson().fromJson(usersWS, MutableList::class.java);
                var studentList: MutableList<User> = mutableListOf()

                for (student in users) {
                    studentList.add(Gson().fromJson(Gson().toJson(student), User::class.java))
                }

                studentList = studentList.filter { u -> u.roleId == 4 }.toMutableList()

                // Update the information
                allStudents = studentList;

                getAllCicles()
                getAllCourses()
                getAllGroups()

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

                try {
                    initializations()
                } catch (ex: Exception) {
                    message(ex.message.toString())
                }

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
            }
        })
    }

    private fun getAllGroups() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/groups").build()

        httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, groupsWS: String) {
                super.onMessage(webSocket, groupsWS)

                // Parsing data
                val groups = Gson().fromJson(groupsWS, MutableList::class.java);
                val groupsList: MutableList<Group> = mutableListOf()

                for (group in groups) {
                    groupsList.add(Gson().fromJson(Gson().toJson(group), Group::class.java))
                }

                // Update the information
                allGroups = groupsList
            }
        })
    }

    private fun getAllEnrollments() {

        val httpClient = OkHttpClient.Builder().pingInterval(40, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("${BuildConfig.BASE_URL_WS}/enrollments").build()

        enrollmentsSocket = httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, enrollmentsWS: String) {
                super.onMessage(webSocket, enrollmentsWS)

                // Parsing data
                val enrollments = Gson().fromJson(enrollmentsWS, MutableList::class.java);
                var enrollmentsList: MutableList<Enrollment> = mutableListOf()

                for (enrollment in enrollments) {
                    enrollmentsList.add(Gson().fromJson(Gson().toJson(enrollment), Enrollment::class.java))
                }

                enrollmentsList = enrollmentsList.filter { u -> u.group.cicleId == selectedCicle }.toMutableList()

                // Update the information
                if (user.roleId != 4) {
                    if(menuType == "Historial"){
                        updateTable(enrollmentsList.filter { e -> e.user.id == selectedStudent?.id }.toMutableList())
                    }else{
                        updateTable(enrollmentsList)
                    }

                } else {
                    updateTable(enrollmentsList.filter { e -> e.user.id == user.id }.toMutableList())
                }
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