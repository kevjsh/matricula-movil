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
        //binding.welcome.text = "Bienvenido " + user.name

        binding.navigation.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.courses -> {
                    val intent = Intent(this, CoursesActivity::class.java).apply {
                        putExtra("User", intent.getStringExtra("User"))
                    }
                    startActivity(intent)
                }

                /* Here we can implement the other option of menu */

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
            Toast.makeText(this,  message, Toast.LENGTH_LONG).show()
        }
    }
}