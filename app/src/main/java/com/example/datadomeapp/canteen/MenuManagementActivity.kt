package com.example.datadomeapp.canteen

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MenuManagementActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddMenu: Button
    private val firestore = FirebaseFirestore.getInstance()
    private val menuList = mutableListOf<MenuItem>()
    private lateinit var adapter: MenuAdapter
    private var menuListener: ListenerRegistration? = null

    private val auth = FirebaseAuth.getInstance()
    private var staffCanteenName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu_management)

        recyclerView = findViewById(R.id.rvMenu)
        btnAddMenu = findViewById(R.id.btnAddMenu)

        adapter = MenuAdapter(menuList,
            onEditClick = { menu ->
                val intent = Intent(this, AddEditMenuActivity::class.java)
                intent.putExtra("menuId", menu.id)
                startActivity(intent)
            },
            onDeleteClick = { menu ->
                firestore.collection("canteenMenu").document(menu.id)
                    .delete()
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnAddMenu.setOnClickListener {
            val intent = Intent(this, AddEditMenuActivity::class.java)
            intent.putExtra("canteenName", staffCanteenName)
            startActivity(intent)
        }

        loadStaffCanteen()
    }

    private fun loadStaffCanteen() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                staffCanteenName = doc.getString("canteenName")
                startMenuListener()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to get staff info: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startMenuListener() {
        val canteen = staffCanteenName ?: return
        menuListener = firestore.collection("canteenMenu")
            .whereEqualTo("canteenName", canteen) // Filter menus by staff's canteen
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Error loading menu: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                menuList.clear()
                snapshot?.documents?.forEach { doc ->
                    val menu = doc.toObject(MenuItem::class.java)
                    menu?.id = doc.id
                    if (menu != null) menuList.add(menu)
                }
                adapter.notifyDataSetChanged()
            }
    }

    override fun onResume() {
        super.onResume()
        startMenuListener()
    }

    override fun onPause() {
        super.onPause()
        menuListener?.remove()
    }
}
