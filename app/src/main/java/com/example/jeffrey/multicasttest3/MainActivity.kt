package com.example.jeffrey.multicasttest3

import android.content.Context
import android.net.wifi.WifiManager
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.recyclerview.widget.SimpleItemAnimator
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.ViewHolder
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.doAsync
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

class MainActivity : AppCompatActivity() {

    private val adapter = GroupAdapter<ViewHolder>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        activity_main_rv_scoreboard.adapter = adapter
        val animator = activity_main_rv_scoreboard.itemAnimator as SimpleItemAnimator
        animator.supportsChangeAnimations = false

        testPopulateScoreboard()

        val handler = Handler(Looper.getMainLooper())
        handler.post(object: Runnable {
            override fun run() {
                testUpdateScoreboard()
                handler.postDelayed(this, 100)
            }
        })

        // Multicast start

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val mLock = wm.createMulticastLock("MulticastLock")
        mLock.setReferenceCounted(true) // pretty sure this line isn't needed because the constructor sets it true by default
        mLock.acquire()

        doAsync {
            val group = InetAddress.getByName("239.0.0.1")
            val socket = MulticastSocket(8888)
            socket.joinGroup(group)

            try {
                while(true) {
                    val bytes = ByteArray(23)
                    val packet = DatagramPacket(bytes, bytes.size)
                    socket.receive(packet)

                    Log.d("Jeffrey", "Data: ${String(packet.data)}")
                }
            } catch(e: Exception) {
                Log.d("Jeffrey", e.message)
            } finally {
                socket.leaveGroup(group)
                socket.close()
                mLock?.release()
            }
        }

        // Multicast end
    }

    private fun testPopulateScoreboard() {
        val names = arrayOf("Albert", "Bobby", "Carol", "Dennis", "Emily", "Felix", "Gary",
            "Hannah", "Ian", "Jacob")
        for(i in 1..10) {
            val racer = Racer(names[i-1], 300, 300, 600, i, "Driving")
            val racerItem = RacerItem(racer)
            adapter.add(i-1, racerItem)
        }
    }

    private fun testUpdateScoreboard() {
        for(i in 0 until adapter.itemCount) {
            (adapter.getItem(i) as RacerItem).racer.apply {
                --timeLeft
            }
            adapter.notifyItemChanged(i)
        }
    }
}
