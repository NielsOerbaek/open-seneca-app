package org.openseneca.sensorapp

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.*


class MainActivity : AppCompatActivity() {

    private val REQUEST_ENABLE_BT = 1
    private val SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val CHARAC_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

    private var outputBuffer = ""

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var curLoc : Location = Location("")

    /* TEXT VIEWS TO MANIPULATE */
    private lateinit var mStatusTextview: TextView
    private lateinit var mLatitudeTextview: TextView
    private lateinit var mLongitudeTextview: TextView
    private lateinit var mLastSendTextview: TextView


    private val bleScanner = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if(result?.device?.name == "HMSoft") {
                Log.d(
                    "ScanDeviceActivity",
                    "onScanResult(): ${result?.device?.address} - ${result?.device?.name}"
                )
                bluetoothLeScanner.stopScan(this)
                val bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val device = bluetoothManager.adapter.getRemoteDevice(result.getDevice().getAddress())
                val gatt = device.connectGatt(applicationContext, true, mGattCallback)
                mStatusTextview.text = resources.getString(R.string.bleConnect)
            }
        }
    }

    private val bluetoothLeScanner: BluetoothLeScanner
        get() {
            val bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
            return bluetoothAdapter.bluetoothLeScanner
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("ScanDeviceActivity", "onCreate()")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /* TEXT VIEWS TO MANIPULATE */
        mStatusTextview = findViewById<TextView>(R.id.status)
        mLatitudeTextview = findViewById<TextView>(R.id.latitude)
        mLongitudeTextview = findViewById<TextView>(R.id.longitude)
        mLastSendTextview = findViewById<TextView>(R.id.lastSend)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        getLocation()
    }

    override fun onStart() {
        Log.d("ScanDeviceActivity", "onStart()")
        super.onStart()
        when (PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            PackageManager.PERMISSION_GRANTED -> {
                Log.d("ScanDeviceActivity", "Started Scan")
                startScan()
            }
            else -> requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
        startService()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            1 -> when (grantResults) {
                intArrayOf(PackageManager.PERMISSION_GRANTED) -> {
                    Log.d("ScanDeviceActivity", "onRequestPermissionsResult(PERMISSION_GRANTED)")
                    startScan()
                }
                else -> {
                    Log.d("ScanDeviceActivity", "onRequestPermissionsResult(not PERMISSION_GRANTED)")
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    fun startScan() {
        mStatusTextview.text = resources.getString(R.string.bleScan)
        bluetoothLeScanner.startScan(bleScanner)
    }

    override fun onStop() {
        Log.d("ScanDeviceActivity", "onStop()")
        bluetoothLeScanner.stopScan(bleScanner)
        super.onStop()
    }

    override fun onDestroy() {
        stopService()
        super.onDestroy()
    }

    fun getLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location : Location? ->
                if (location != null) {
                    curLoc = location
                    updateLocView(location)
                }
                else {
                    Log.e("ScanDeviceActivity", "Null Location!")
                    Toast.makeText(getApplicationContext(),"Please turn on GPS",Toast.LENGTH_SHORT).show();
                    Handler().postDelayed({
                        stopService()
                        finishAffinity()
                        System.exit(0)
                    }, 3000)
                }
            }
    }


    fun updateLocView(loc: Location) {
        mLatitudeTextview.text = loc.latitude.toString()
        mLongitudeTextview.text = loc.longitude.toString()
    }


    fun sendReading(output: String) {
        mStatusTextview.text = resources.getString(R.string.bleSending)
        val res = buildResponse(output)
        val responseCode: Int = sendPostRequest(res.toString())
        val lastSend = resources.getString(R.string.lastSend, res.get("time") as String, responseCode.toString())
        mStatusTextview.text = resources.getString(R.string.bleSuccess)
        mLastSendTextview.text = lastSend
        Log.d("ScanDeviceActivity", "Last Send : $lastSend")

    }

    fun buildResponse(output: String) : JSONObject {
        getLocation()
        val res = JSONObject()
        val headers = "Counter,Latitude,Longitude,gpsUpdated,Speed,Altitude,Satellites,Date,Time,Millis,PM1.0,PM2.5,PM4.0,PM10,Temperature,Humidity,NC0.5,NC1.0,NC2.5,NC4.0,NC10,TypicalParticleSize,TVOC,eCO2,BatteryVIN".split(",")
        res.put("headers", JSONArray(headers))
        val values = output.split(",").map{x -> x.toDouble()}
        res.put("values", JSONArray(values))
        val now = Date()
        res.put("time", now.toString())
        res.put("timestamp", now.time/1000)
        val loc = JSONObject()
        loc.put("lat", curLoc.latitude)
        loc.put("lon", curLoc.longitude)
        loc.put("altitude", curLoc.altitude)
        loc.put("speed", curLoc.speed)
        loc.put("accuracy", curLoc.accuracy)
        loc.put("fixTime", curLoc.time)
        loc.put("fixAge", (now.time-curLoc.time)/1000)
        res.put("location", loc)
        return res
    }


    fun sendPostRequest(msg: String) : Int {
        val reqParam = URLEncoder.encode("data", "UTF-8") + "=" + URLEncoder.encode(msg, "UTF-8")
        val mURL = URL("http://influxus.itu.dk/openseneca/store.php")

        with(mURL.openConnection() as HttpURLConnection) {
            // optional default is GET
            requestMethod = "POST"

            Log.d("ScanDeviceActivity", "reqParam : $reqParam")

            val wr = OutputStreamWriter(getOutputStream());
            wr.write(reqParam);
            wr.flush();

            Log.d("ScanDeviceActivity","URL : $url")
            Log.d("ScanDeviceActivity","Response Code : $responseCode")

            BufferedReader(InputStreamReader(inputStream)).use {
                val response = StringBuffer()

                var inputLine = it.readLine()
                while (inputLine != null) {
                    response.append(inputLine)
                    inputLine = it.readLine()
                }
                it.close()
                Log.d("ScanDeviceActivity", "Response : $response")
            }
            return responseCode
        }
    }

    fun startService() {
        val serviceIntent = Intent(this, SensorService::class.java)
        //serviceIntent.putExtra("inputExtra", "Foreground Service Example in Android")
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    fun stopService() {
        val serviceIntent = Intent(this, SensorService::class.java)
        stopService(serviceIntent)
        Log.d("ScanDeviceActivity", "Stopped Service")
    }

    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if(status == BluetoothGatt.GATT_READ_NOT_PERMITTED) Log.d("ScanDeviceActivity", "onConnectionStateChange: READ NOT PERMITTED")
            else if(status == BluetoothGatt.GATT_SUCCESS) Log.d("ScanDeviceActivity", "onConnectionStateChange: SUCCESS")
            else Log.d("ScanDeviceActivity", "onConnectionStateChange: " + status)
            gatt?.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.d("ScanDeviceActivity", "onServicesDiscovered, Status: " + status)
            if (gatt == null) return
            /* For debugging: Scan for all services and characteristics
            for (service in gatt.getServices()) {
                Log.d(
                    "ScanDeviceActivity",
                    "onServicesDiscovered, service: " + service.getUuid().toString()
                )
                for (c in service.getCharacteristics()) {
                    Log.d(
                        "ScanDeviceActivity",
                        "Characteristic: " + c.getUuid().toString()
                    )
                }
            }*/
            val mCharacteristic = gatt.getService(SERVICE_UUID).getCharacteristic(CHARAC_UUID)
            gatt.setCharacteristicNotification(mCharacteristic, true)
            mStatusTextview.text = resources.getString(R.string.bleSuccess)
        }

        override fun onCharacteristicChanged (gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            mStatusTextview.text = resources.getString(R.string.bleSuccess)
            outputBuffer += String(characteristic.getValue())
            if (outputBuffer.contains("\n")) {
                Log.d(
                    "ScanDeviceActivity",
                    "onCharacteristicChanged(): Message ended with newline. Sending."
                )
                val split = outputBuffer.split("\n")
                sendReading(split[0].trim())
                outputBuffer = split[1].trim()
            }
        }
    }
}

