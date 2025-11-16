package com.example.datadomeapp.enrollment

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.datadomeapp.R

class HintAdapter(
    context: Context,
    resource: Int,
    private val items: List<String>
) : ArrayAdapter<String>(context, resource, items) {

    override fun isEnabled(position: Int): Boolean {
        // Disable the first item (hint item)
        return position != 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view as TextView

        // Style the hint item differently in the main spinner view
        if (position == 0) {
            textView.setTextColor(ContextCompat.getColor(context, R.color.gray_hint_color))
        } else {
            textView.setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }

        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        val textView = view as TextView

        // Style the disabled item differently in dropdown
        if (position == 0) {
            textView.setTextColor(ContextCompat.getColor(context, R.color.gray_hint_color))
        } else {
            textView.setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }

        return view
    }
}