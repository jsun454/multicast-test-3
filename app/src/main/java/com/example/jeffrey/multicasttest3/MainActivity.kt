package com.example.jeffrey.multicasttest3

import android.content.Context
import android.net.wifi.WifiManager
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.SparseArray
import androidx.recyclerview.widget.SimpleItemAnimator
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.ViewHolder
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

class MainActivity : AppCompatActivity() {

    private val adapter = GroupAdapter<ViewHolder>()

    @ExperimentalUnsignedTypes
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        activity_main_rv_scoreboard.adapter = adapter
        val animator = activity_main_rv_scoreboard.itemAnimator as SimpleItemAnimator
        animator.supportsChangeAnimations = false

        /* FOR TESTING SCOREBOARD UPDATES WITHOUT MULTICAST **
        testPopulateScoreboard()

        val handler = Handler(Looper.getMainLooper())
        handler.post(object: Runnable {
            override fun run() {
                testUpdateScoreboard()
                handler.postDelayed(this, 100)
            }
        })
        ** FOR TESTING SCOREBOARD UPDATES WITHOUT MULTICAST */

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
                    val bytes = ByteArray(24)
                    val packet = DatagramPacket(bytes, bytes.size)
                    socket.receive(packet)

                    Log.d("Jeffrey", "Data: ${String(packet.data)}")

                    /* DISPLAY DATA ON SCOREBOARD */
                    // 1 byte kart #
                    // 1 byte status (0=idle, 1=in preparation, 2=driving)
                    // 1 byte lap # (negative=final lap)
                    // 1 byte total # laps
                    // 4 bytes (uint) previous lap time (ms)
                    // 4 bytes (uint) best lap time (ms)
                    // 10 bytes (char[10]) driver's name
                    // 1 byte checkSum (idk, ignore for now)
                    // 1 byte mode (0=distance mode, display laps left/total laps; 1=time mode, display time left/total time)
                    val kartNumber = bytes[0].toUByte().toInt()
                    val status = when(bytes[1].toInt()) {
                        0 -> "Idle"
                        1 -> "In Preparation"
                        2 -> "Driving"
                        else -> "Error"
                    }
                    val lapNumber = when(bytes[2].toInt() >= 0) { // lap number probably shouldn't equal 0 either
                        true -> bytes[2].toInt()
                        false -> bytes[3].toInt()
                    }
                    val totalNumLaps = bytes[3]
                    val prevLapTime = (((bytes[4].toUInt() and 0xFFu) shl 24) or
                            ((bytes[5].toUInt() and 0xFFu) shl 16) or
                            ((bytes[6].toUInt() and 0xFFu) shl 8) or
                            (bytes[7].toUInt() and 0xFFu)).toInt()
                    var bestLapTime = (((bytes[8].toUInt() and 0xFFu) shl 24) or
                            ((bytes[9].toUInt() and 0xFFu) shl 16) or
                            ((bytes[10].toUInt() and 0xFFu) shl 8) or
                            (bytes[11].toUInt() and 0xFFu)).toInt()
                    val name = String(bytes.sliceArray(12..21))
                    val checkSum = bytes[22]
                    val isTimeMode = (bytes[23].toInt() == 1) // assume false for now

                    if(bestLapTime > prevLapTime) {
                        bestLapTime = prevLapTime // update best lap
                    }

                    var foundExistingRacer = false
                    for(i in 0 until adapter.itemCount) {
                        if((adapter.getItem(i) as RacerItem).racer.kartNO == kartNumber) {
                            (adapter.getItem(i) as RacerItem).apply {
                                racer.name = name
                                racer.lapTime = prevLapTime
                                racer.bestLap = bestLapTime
                                racer.timeOrDistanceLeft = totalNumLaps-lapNumber+1
                                racer.status = status
                            }
                            adapter.notifyItemChanged(i)
                            foundExistingRacer = true
                            break
                        }
                    }
                    if(!foundExistingRacer) {
                        val racer = Racer(name, prevLapTime, bestLapTime, totalNumLaps-lapNumber+1, kartNumber, status)
                        val racerItem = RacerItem(racer)
                        uiThread {
                            adapter.add(racerItem)
                        }
                    }
                    /* DISPLAY DATA ON SCOREBOARD */
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
                --timeOrDistanceLeft
            }
            adapter.notifyItemChanged(i)
        }
    }
}
