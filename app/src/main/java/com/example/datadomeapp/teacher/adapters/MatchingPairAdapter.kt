package com.example.datadomeapp.teacher.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView // Kailangan para sa "X" button
import androidx.recyclerview.widget.RecyclerView
// Tinanggal ang import ng ImageButton dahil gagamitin na ang TextView
import com.example.datadomeapp.R
import com.example.datadomeapp.teacher.MatchingPair

class MatchingPairAdapter(
    private val pairs: MutableList<MatchingPair>,
    private val removePairCallback: (Int) -> Unit
) : RecyclerView.Adapter<MatchingPairAdapter.PairViewHolder>() {

    // Wala nang MyCustomTextWatcher class dito.

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PairViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_matching_pair, parent, false)
        // NABAGO: Wala nang MyCustomTextWatcher na pinapasa
        return PairViewHolder(view)
    }

    override fun onBindViewHolder(holder: PairViewHolder, position: Int) {
        // NABAGO: Isang parameter lang ang tinatanggap ng bind method
        holder.bind(pairs[position])
    }

    override fun getItemCount(): Int = pairs.size

    fun getData(): List<MatchingPair> = pairs

    // ----------------------------------------------------
    // INAYOS NA PairViewHolder: Wala nang MyCustomTextWatcher sa constructor
    // ----------------------------------------------------
    inner class PairViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val etLeftTerm: EditText = itemView.findViewById(R.id.etLeftTerm)
        private val etRightMatch: EditText = itemView.findViewById(R.id.etRightMatch)
        private val btnRemovePair: TextView = itemView.findViewById(R.id.btnRemovePair) // TextView (para sa "X")

        // 1. ANONYMOUS TEXTWATCHER para sa etLeftTerm (Fixes Runtime Bug)
        private val leftWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    pairs[adapterPosition].leftTerm = s.toString()
                }
            }
        }

        // 2. ANONYMOUS TEXTWATCHER para sa etRightMatch (Fixes Runtime Bug)
        private val rightWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    pairs[adapterPosition].rightMatch = s.toString()
                }
            }
        }


        fun bind(pair: MatchingPair) {

            // HAKBANG 1: Tanggalin ang TextWatcher bago mag-set ng bagong text
            // Ito ang critical para sa RecyclerView recycling!
            etLeftTerm.removeTextChangedListener(leftWatcher)
            etRightMatch.removeTextChangedListener(rightWatcher)

            // HAKBANG 2: I-set ang data
            etLeftTerm.setText(pair.leftTerm)
            etRightMatch.setText(pair.rightMatch)

            // HAKBANG 3: I-set up ang TextWatcher muli
            etLeftTerm.addTextChangedListener(leftWatcher)
            etRightMatch.addTextChangedListener(rightWatcher)

            // HAKBANG 4: I-set up ang Remove button
            btnRemovePair.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    removePairCallback(adapterPosition)
                }
            }
        }
    }
}