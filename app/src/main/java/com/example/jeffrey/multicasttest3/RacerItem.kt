package com.example.jeffrey.multicasttest3

import com.xwray.groupie.Item
import com.xwray.groupie.ViewHolder
import kotlinx.android.synthetic.main.racer_row.view.*

class RacerItem(val racer: Racer) : Item<ViewHolder>() {
    override fun getLayout(): Int {
        return R.layout.racer_row
    }

    override fun bind(viewHolder: ViewHolder, position: Int) {
        viewHolder.itemView.apply {
            racer_row_txt_name.text = racer.name
            racer_row_txt_lap_time.text = racer.lapTime.toString()
            racer_row_txt_best_lap.text = racer.bestLap.toString()
            racer_row_txt_time_left.text = racer.timeLeft.toString()
            racer_row_txt_kart_number.text = racer.kartNO.toString()
            racer_row_txt_status.text = racer.status
        }
    }
}