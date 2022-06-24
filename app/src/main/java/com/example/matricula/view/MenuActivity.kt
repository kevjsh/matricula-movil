package com.example.matricula.view

import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.example.matricula.R
import com.example.matricula.databinding.ActivityMenuBinding
import com.example.matricula.model.User
import com.google.gson.Gson
import kotlin.system.exitProcess

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user: User = Gson().fromJson(intent.getStringExtra("User"), User::class.java)

        if (user.roleId == 1) {
            binding.navigation.menu.getItem(0).setVisible(true)
            binding.navigation.menu.getItem(1).setVisible(true)
            binding.navigation.menu.getItem(2).setVisible(true)
            binding.navigation.menu.getItem(3).setVisible(true)
            binding.navigation.menu.getItem(4).setVisible(true)
            binding.navigation.menu.getItem(5).setVisible(true)
            binding.navigation.menu.getItem(6).setVisible(true)
            binding.navigation.menu.getItem(8).setVisible(true)
            binding.navigation.menu.getItem(9).setVisible(true)
        } else if (user.roleId == 2) {
            binding.navigation.menu.getItem(6).setVisible(true)
        } else if (user.roleId == 3) {
            binding.navigation.menu.getItem(7).setVisible(true)
        } else if (user.roleId == 4) {
            binding.navigation.menu.getItem(8).setVisible(true)
        }

        binding.navigation.setNavigationItemSelectedListener { item ->
            when (item.itemId) {

                /* Courses */
                R.id.courses -> {
                    val intent = Intent(this, CoursesActivity::class.java).apply {
                        putExtra("User", intent.getStringExtra("User"))
                    }
                    startActivity(intent)
                }
                /* Careers */
                R.id.careers -> {
                    val intent = Intent(this, CareerActivity::class.java).apply {
                        putExtra("User", intent.getStringExtra("User"))
                    }
                    startActivity(intent)
                }

                /* Cicles */
                R.id.cicles -> {
                    val intent = Intent(this, CicleActivity::class.java).apply {
                        putExtra("User", intent.getStringExtra("User"))
                    }
                    startActivity(intent)
                }

                /* General users */
                R.id.students -> {
                    val intent = Intent(this, UsersActivity::class.java).apply {
                        putExtra("User", intent.getStringExtra("User"))
                        putExtra("Usertype", "Alumnos")
                    }
                    startActivity(intent)
                }

                R.id.professors -> {
                    val intent = Intent(this, UsersActivity::class.java).apply {
                        putExtra("User", intent.getStringExtra("User"))
                        putExtra("Usertype", "Profesores")
                    }
                    startActivity(intent)
                }

                R.id.security -> {
                    val intent = Intent(this, UsersActivity::class.java).apply {
                        putExtra("User", intent.getStringExtra("User"))
                        putExtra("Usertype", "Seguridad")
                    }
                    startActivity(intent)
                }

                /* Enrollment and History */
                R.id.enrollment -> {
                    val intent = Intent(this, EnrollmentActivity::class.java).apply {
                        putExtra("User", intent.getStringExtra("User"))
                        putExtra("Menutype", "Matrícula")
                    }
                    startActivity(intent)
                }
                R.id.history -> {
                    val intent = Intent(this, EnrollmentActivity::class.java).apply {
                        putExtra("User", intent.getStringExtra("User"))
                        putExtra("Menutype", "Historial")
                    }
                    startActivity(intent)
                }

                /* Groups */
                R.id.groups -> {
                    val intent = Intent(this, GroupActivity::class.java).apply {
                        putExtra("User", intent.getStringExtra("User"))
                    }
                    startActivity(intent)
                }

                /* Grades */
                R.id.grades -> {
                    val intent = Intent(this, GradesActivity::class.java).apply {
                        putExtra("User", intent.getStringExtra("User"))
                    }
                    startActivity(intent)
                }

            }
            false
        }

    }

    override fun onBackPressed() {

        val mBuilder = AlertDialog.Builder(this)
            .setTitle("Salir del sistema")
            .setMessage("¿Seguro que desea cerrar la sesión?")
            .setPositiveButton("Sí", null)
            .setNegativeButton("No", null)
            .show()

        val mPositiveButton = mBuilder.getButton(AlertDialog.BUTTON_POSITIVE)
        mPositiveButton.setOnClickListener {
            exitProcess(0)
        }
    }

    /* Fast message on screen */
    private fun message(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}