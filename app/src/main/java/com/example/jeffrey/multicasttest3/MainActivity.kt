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
import android.util.SparseIntArray
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
    private val karts = SparseIntArray() // stores (kart #, adapter pos + 1)

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

            while(true) {
                try {
                    val bytes = ByteArray(24)
                    val packet = DatagramPacket(bytes, bytes.size)
                    socket.receive(packet)

//                    Log.d("Jeffrey", "Data: ${String(packet.data)}")

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

                    if(bestLapTime > prevLapTime || bestLapTime == 0) {
                        bestLapTime = prevLapTime // update best lap
                    }

                    Log.d("Jeffrey", "Kart: ${kartNumber%10}   Lap Time: $prevLapTime   Best Lap: $bestLapTime")

                    if(karts[kartNumber] == 0) {
                        Log.d("Jeffrey", "Not found ${kartNumber%10}")
                        val racer = Racer(name, prevLapTime, bestLapTime, totalNumLaps-lapNumber+1, kartNumber, status)
                        val racerItem = RacerItem(racer)
                        uiThread {
                            // not sure if checking right before adding prevents duplicates
                            // I may need to just remove duplicates after the fact
                            if(karts[kartNumber] != 0) return@uiThread
                            adapter.add(racerItem)
                            // pos is 1 more than the actual position to avoid storing value=0 in the SparseIntArray
                            // (because value=0 if no key-value pair is stored for a given key (kart number))
                            val pos = adapter.itemCount
                            karts.put(kartNumber, pos)
                            if((adapter.getItem(pos-1) as RacerItem).racer.kartNO != kartNumber) {
                                // not sure if this check is necessary
                                // if this ever happens then add code to loop through adapter items to find the position
                                //  of the kart and save it in the SparseIntArray (karts)
                                Log.e("Jeffrey", "Wrong kart number at adapter position $pos")
                            }
                        }
                    } else {
                        val racerItem = adapter.getItem(karts[kartNumber]-1) as RacerItem
                        if(racerItem.racer.kartNO == kartNumber) {
                            racerItem.apply {
                                racer.name = name
                                racer.lapTime = prevLapTime
                                racer.bestLap = bestLapTime
                                racer.timeOrDistanceLeft = totalNumLaps-lapNumber+1
                                racer.status = status
                            }
                            uiThread {
                                Log.d("Jeffrey", "Update: ${kartNumber%10}   Lap Time: ${racerItem.racer.lapTime}   " +
                                        "Best Lap: ${racerItem.racer.bestLap}")
                                adapter.notifyItemChanged(karts[kartNumber]-1)
                            }
                        }
                    }
                    /* DISPLAY DATA ON SCOREBOARD */
                } catch(e: Exception) {
                    Log.e("Jeffrey", e.message)
                } finally {
                    // move these elsewhere or delete
//                    socket.leaveGroup(group)
//                    socket.close()
//                    mLock?.release()
                }
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
