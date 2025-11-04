package com.example.datadomeapp.student

import android.os.Bundle
import android.util.Base64
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import android.graphics.BitmapFactory

class UserCanteenItemDetailActivity : AppCompatActivity() {

    private lateinit var ivItemImage: ImageView
    private lateinit var tvItemName: TextView
    private lateinit var tvItemPrice: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_canteen_item_detail)

        ivItemImage = findViewById(R.id.ivItemImage)
        tvItemName = findViewById(R.id.tvItemName)
        tvItemPrice = findViewById(R.id.tvItemPrice)

        val name = intent.getStringExtra("ITEM_NAME") ?: ""
        val price = intent.getDoubleExtra("ITEM_PRICE", 0.0)
        val imageBase64 = intent.getStringExtra("ITEM_IMAGE_BASE64") ?: ""

        tvItemName.text = name
        tvItemPrice.text = "₱${String.format("%.2f", price)}"

        if (imageBase64.isNotEmpty()) {
            val bytes = Base64.decode(imageBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ivItemImage.setImageBitmap(bitmap)
        }
    }
}
