package com.example.datadomeapp.enrollment

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat

class NonSelectableArrayAdapter(
    context: Context,
    resource: Int,
    private val items: List<String>
) : ArrayAdapter<String>(context, resource, items) {

    override fun isEnabled(position: Int): Boolean {
        return position != 0
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        val textView = view as TextView

        if (position == 0) {
            view.isEnabled = false
            textView.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        } else {
            view.isEnabled = true
            textView.setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }

        return view
    }
}