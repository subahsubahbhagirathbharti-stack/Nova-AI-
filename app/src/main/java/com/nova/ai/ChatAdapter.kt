package com.nova.ai

import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val items: List<Msg>) : RecyclerView.Adapter<ChatAdapter.VH>() {
    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv.parent as android.view.View)

    override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
        val v = LayoutInflater.from(p.context).inflate(R.layout.item_message, p, false)
        return VH(v.findViewById(R.id.bubble))
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, i: Int) {
        val m = items[i]
        h.tv.text = m.text
        val mine = m.role == "user"
        h.tv.setBackgroundResource(if (mine) R.drawable.bg_user else R.drawable.bg_nova)
        (h.tv.layoutParams as LinearLayout.LayoutParams).gravity = if (mine) Gravity.END else Gravity.START
        h.tv.typeface = if (m.text.contains("```")) Typeface.MONOSPACE else Typeface.DEFAULT
    }
}
