package com.goodwin.director;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.goodwin.director.GpsManager.GpsManagerListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.UUID;

/**
 * Main activity for a data gathering tool. This tool enables recording video
 * and collecting data from sensors. Data is stored in:
 * /sdcard/SmartphoneLoggerData/.
 *
 * @author clchen@google.com (Charles L. Chen)
 */

public class DirectorActivity extends Activity implements SeekBar.OnSeekBarChangeListener {
    /*
     * Constants
     */
    public static final String TAG = "DIRECTOR";

    private TextView txtArduino;
    private TextView mGpsLocationView;
    private TextView mAngleView;
    private TextView mDistanceView;
    private TextView mRemainingPositionsView;
    private TextView mDistBufferValueView;
    private TextView btStateView;
    private ListView btListView;

    private ImageView mCompassView;

    private SeekBar mBufferSensitivity;

    public CheckBox leftRev, rightRev;

    private Button btScanDeviceButton;

    BluetoothAdapter mBluetoothAdapter;
    Set<BluetoothDevice> pairedDevices;
    private ArrayAdapter btArrayAdapter;
    private GpsManager mGpsManager;
    private Handler mHandler = new Handler();
    private Timer mGPSPoller = new Timer();

    Handler h;

    final int RECIEVE_MESSAGE = 1;		// Status  for Handler
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder sb = new StringBuilder();
    public ConnectedThread mConnectedThread;
    private static String address = "20:13:05:13:11:42";
    //private static String address = "00:17:F2:B2:42:2D";

    private String lastSeenValues;
    private Context DirAct = this;

    private ArrayList destinationLatLng = new ArrayList();
    private List<Sensor> sensors;
    public static SensorManager sensorManager;
    private int sensorNumber;
    private Boolean supported;
    private float azimuth = 0;
    private float destCurLocAngle = 0;
    private float destBufferDist = 5;

    private static int REQUEST_ENABLE_BT = 32;
    private static final UUID LINVOR_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private boolean connectita = false;
    Balancer balancer;

    private static int REQUEST_CODE_WAYPOINTS = 110;
    private ArrayList<ArrayList<Double>> latLng;
    private Intent getWayPointsIntent = new Intent();
    /*
     * Runnables
     */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //initBlueTooth();
        getWayPointsIntent.setClassName("com.goodwin.director","com.goodwin.director.MappingActivity");
        startActivityForResult(getWayPointsIntent, REQUEST_CODE_WAYPOINTS);
        initMain();
        initBT();
        //setupSensors();
    }


    public boolean isSupported() {
        if (supported == null) {
            if (sensorManager != null) {
                if (sensors != null) {
                    for (int i = 0; i < sensors.size(); i++) {
                        Sensor s = sensors.get(i);
                        if (s.getName().equals("iNemoEngine Orientation sensor")) {
                            supported = Boolean.TRUE;
                            sensorNumber = i;
                            break;
                        } else {
                            supported = Boolean.FALSE;
                        }
                    }
                }
            }
        }
        return supported;
    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        if (isSupported()) {
            sensorManager.registerListener(
                    mSensorEventListener, sensors.get(sensorNumber), SensorManager.SENSOR_DELAY_GAME);
        }
        initGps();

        //mHandler.postDelayed(updateCompass,1000); //Not started until 'Navigator' is clicked s.t. one can store locations
        // w/o then being cleared
        /*mGPSPoller.schedule(new TimerTask() {
            @Override
            public void run() {
                Toast.makeText(
                        DirAct,
                        "Toast works.",
                        Toast.LENGTH_SHORT).show();
                //if (!destinationLatLng[0].equals("")) {
                //    destCurLocAngle = (float) calculateAngle();
                //}
            }
        }, 1000, 1000);*/

    }

    @Override
    protected void onResume() {
        super.onResume();
        onResumeMain();
        onResumeBT();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i("LoggerActivity", "onPause called");
        onPauseMain();
        onPauseBT();
        Log.i("LoggerActivity", "onPause finished.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_WAYPOINTS) {
            if(resultCode == RESULT_OK) {
                latLng = (ArrayList<ArrayList<Double>>) data.getSerializableExtra("LatLngString");
                for (ArrayList<Double> value : latLng) {
                    destinationLatLng.add(value.get(0).toString() + "," + value.get(1).toString());
                }
            }
        }
    }

    private void initMain() {
        setContentView(R.layout.director_activity);

        mGpsLocationView = (TextView) findViewById(R.id.gpsLocationCompass);
        mAngleView = (TextView) findViewById(R.id.Angle);
        mDistanceView = (TextView) findViewById(R.id.distanceFromSet);
        mRemainingPositionsView = (TextView) findViewById(R.id.remainPos);
        mCompassView = (ImageView) findViewById(R.id.compass);
        mBufferSensitivity = (SeekBar) findViewById(R.id.destBufferSensitivity);
        mBufferSensitivity.setOnSeekBarChangeListener(this);
        mDistBufferValueView = (TextView) findViewById(R.id.distBufferValue);
        leftRev = (CheckBox) findViewById(R.id.revLeftServo);
        rightRev = (CheckBox) findViewById(R.id.revRightServo);

        balancer = new Balancer();

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Stops going to sleep

        //leftRev = (CheckBox) findViewById(R.id.leftServoVal);
        //rightRev = (CheckBox) findViewById(R.id.leftServoVal);

        final Button recordButton = (Button) findViewById(R.id.recordLocation);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastSeenValues != null) {
                    destinationLatLng.add(lastSeenValues);
                    updatePositionNos();
                }
            }
        });

        final Button navigatorStartButton = (Button) findViewById(R.id.startNavigator);
        navigatorStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.post(updateCompass);
                startBalancer();
            }
        });

        final Button navigatorStopButton = (Button) findViewById(R.id.stopNavigator);
        navigatorStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopUpdateCompass();
                stopBalancer();
            }
        });

        final Button northReset = (Button) findViewById(R.id.northReset);
        northReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                destCurLocAngle = 0;
            }
        });
    }

    private void onResumeMain () {
        setupSensors();
    }

    private void onPauseMain() {
        //unregisterReceiver(ActionFoundReceiver);
        stopToastTest();
        stopUpdateCompass();
        // Does the gps cleanup/file closing
        cleanup();
        stopBalancer();
        // Unregister iNemoEngine sensor listener
        sensorManager.unregisterListener(mSensorEventListener, sensors.get(sensorNumber));
    }

    private void initBT() {
        txtArduino = (TextView) findViewById(R.id.txtArduino);		// for display the received data from the Arduino

        h = new Handler() {
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case RECIEVE_MESSAGE:													// if receive massage
                        byte[] readBuf = (byte[]) msg.obj;
                        String strIncom = new String(readBuf, 0, msg.arg1);					// create string from bytes array
                        sb.append(strIncom);												// append string
                        int endOfLineIndex = sb.indexOf("\r\n");							// determine the end-of-line
                        int lastEOL = sb.lastIndexOf("\r\n");
                        if (endOfLineIndex > 0) { 											// if end-of-line,
                            String sbprint = sb.substring(0, endOfLineIndex);				// extract string
                            sb.delete(0, sb.length());                                      // and clear
                            txtArduino.setText("Data from Arduino: " + sbprint); 	        // update TextView
                        } else if (endOfLineIndex == 0 && lastEOL > 0) {
                            String sbprint = sb.substring(2,sb.substring(2).lastIndexOf("\r\n")); // extract string
                            sb.delete(0, sb.length());                                      // and clear
                            txtArduino.setText("Data from Arduino: " + sbprint); 	        // update TextView
                            //btnOff.setEnabled(true);
                            //btnOn.setEnabled(true);
                        }
                        Log.d(TAG, "...String:"+ sb.toString() +  "Byte:" + msg.arg1 + "...");
                        break;
                }
            };
        };

        btAdapter = BluetoothAdapter.getDefaultAdapter();		// get Bluetooth adapter
        checkBTState();
    }

    private void onResumeBT() {
        Log.d(TAG, "...onResume - try connect...");

        // Set up a pointer to the remote node using it's address.
        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        // Two things are needed to make a connection:
        //   A MAC address, which we got above.
        //   A Service ID or UUID.  In this case we are using the
        //     UUID for SPP.

        try {
            btSocket = createBluetoothSocket(device);
        } catch (IOException e) {
            errorExit("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
        }

    /*try {
      btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
    } catch (IOException e) {
      errorExit("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
    }*/

        // Discovery is resource intensive.  Make sure it isn't going on
        // when you attempt to connect and pass your message.
        btAdapter.cancelDiscovery();

        // Establish the connection.  This will block until it connects.
        Log.d(TAG, "...Connecting...");
        try {
            btSocket.connect();
            Log.d(TAG, "....Connection ok...");
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                errorExit("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
            }
        }

        // Create a data stream so we can talk to server.
        Log.d(TAG, "...Create Socket...");

        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();
    }

    public void onPauseBT() {
        Log.d(TAG, "...In onPause()...");

        try     {
            btSocket.close();
        } catch (IOException e2) {
            errorExit("Fatal Error", "In onPause() and failed to close socket." + e2.getMessage() + ".");
        }
    }

    private void checkBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        // Emulator doesn't support Bluetooth and will return null
        if(btAdapter==null) {
            errorExit("Fatal Error", "Bluetooth not support");
        } else {
            if (btAdapter.isEnabled()) {
                Log.d(TAG, "...Bluetooth ON...");
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    private void errorExit(String title, String message){
        Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
        finish();
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        if(Build.VERSION.SDK_INT >= 10){
            try {
                final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
                return (BluetoothSocket) m.invoke(device, LINVOR_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Could not create Insecure RFComm Connection",e);
            }
        }
        return  device.createRfcommSocketToServiceRecord(LINVOR_UUID);
    }

    public class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);		// Get number of bytes and message in "buffer"
                    h.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();		// Send to message queue Handler
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] message) {
            Log.d(TAG, "...Data to send: " + byteArrayToIntString(message) + "...");
            try {
                mmOutStream.write(message);
            } catch (IOException e) {
                Log.d(TAG, "...Error data send: " + e.getMessage() + "...");
            }
        }
    }

    public String byteArrayToIntString (byte[] arr) {
        StringBuilder byteInts = new StringBuilder();
        for (byte c : arr) {
            byteInts.append((int) c);
        }
        return byteInts.toString();
    }

    private SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            Sensor sensor = event.sensor;
            if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                // Gyroscope doesn't really have a notion of accuracy.
                // Due to a bug in Android, the gyroscope incorrectly returns
                // its status as unreliable. This can be safely ignored and does
                // not impact the accuracy of the readings.
                event.accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;
            }
            azimuth = event.values[0];
            mHandler.post(reorientCompass);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private double calculateAngle() {
        //double curLat = Double.parseDouble(lastSeenValues.get("GPS").split(",")[2]);
        //double curLng = Double.parseDouble(lastSeenValues.get("GPS").split(",")[3]);
        double result;
        double curLat = Double.parseDouble(lastSeenValues.split(",")[0]);
        double curLng = Double.parseDouble(lastSeenValues.split(",")[1]);
        double destLat = Double.parseDouble(destinationLatLng.get(0).toString().split(",")[0]);
        double destLng = Double.parseDouble(destinationLatLng.get(0).toString().split(",")[1]);
        // The following is an approximation and doesn't implement spherical triangles - use an external library to do this later
        double y = 1.852 * 60 * 1000 * (destLat - curLat);
        double x = 1.852 * 60 * 1000 * Math.cos(destLat/180*Math.PI)*(destLng - curLng);
        result = 90 - (Math.atan2(y,x) * 180 / Math.PI);
        // transform from polar to bearing
        if (mAngleView != null) {
            mAngleView.setText("Angle: " + result);
            balancer.requestedAzimuth = (float) result;
            /*Toast.makeText(
                    DirAct,
                    "" + result,
                    Toast.LENGTH_SHORT).show();*/
            //(Math.signum(180 - result)*(180-Math.abs(180-result))); // No longer has to be b/w -180 and 180
            // must be between -180 and 180
        }
        if (mDistanceView != null) {
            mDistanceView.setText("Lat(m): " + y + "; Lon(m): " + x + "; Dist(m):" + Math.sqrt(y * y + x * x));
        }
        if ((y * y + x * x) < destBufferDist) {
            destinationLatLng.remove(0);
            updatePositionNos();
            Toast.makeText(
                    DirAct,
                    "Waypoint reached!",
                    Toast.LENGTH_SHORT).show();
        }

        return result;
    }

    private Runnable toastTest = new Runnable() {
        public void run() {
            if (destinationLatLng.size() > 0){
                Toast.makeText(
                        DirAct,
                        "Toast works.",
                        Toast.LENGTH_SHORT).show();
                destCurLocAngle = (float) calculateAngle();
            }
            mHandler.postDelayed(this,10000);
        }
    };

    public void updatePositionNos() {
        if (mRemainingPositionsView != null) {
            mRemainingPositionsView.setText("Positions remaining: " + destinationLatLng.size());
        }
    }

    private Runnable updateCompass = new Runnable() {
        public void run() {
            if (destinationLatLng.size() > 0){
                destCurLocAngle = (float) calculateAngle(); // Rotation not set b/c will occur very often by reorientCompass anyhow
            }
            mHandler.postDelayed(this,1000);
        }
    };

    private Runnable reorientCompass = new Runnable() {
        public void run() {
            mCompassView.setRotation(destCurLocAngle - azimuth);
        }
    };

    private Runnable mShell = new Runnable () {
        public void run () {
            Thread background = new Thread(updateCompass);
            background.start();
            mHandler.post(this);
        }
    };

    private void checkBluetoothState(){
        if (mBluetoothAdapter == null){
            btStateView.setText("Bluetooth NOT supported");
        }else{
            if (mBluetoothAdapter.isEnabled()){
                if(mBluetoothAdapter.isDiscovering()){
                    btStateView.setText("Bluetooth is currently in device discovery process.");
                }else{
                    btStateView.setText("Bluetooth is enabled.");
                    btScanDeviceButton.setEnabled(true);
                }
            }else{
                btStateView.setText("Bluetooth is NOT enabled!");
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    private final BroadcastReceiver ActionFoundReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                btArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                btArrayAdapter.notifyDataSetChanged();
            }
        }};

    private void initGps() {
        mGpsManager = new GpsManager(this, new GpsManagerListener() {

            // @Override
            // @Override
            public void onGpsLocationUpdate(long time, float accuracy, double latitude,
                                            double longitude, double altitude, float bearing, float speed) {
                lastSeenValues = latitude + "," + longitude;
                // Handler fails to send message here - not sure why but will just poll lastSeenValues - unfortunately this leads
                // to various uncertainties such as timing of the last update - though there is always some noise in the GPS so
                // should be safe to assume that if location is exactly the same nothing has changed
                // I noted that reorientCompass was being attempted which might crash due to destinationLatLng being undefined,
                // but updateCompass also failed so it's not the source of the problem.
                //lastSeenValues.put("Lng",lng);
                //try {
                //mHandler.post(updateCompass);
                //} catch (Exception e) {
                //    Log.e("onGPSLocUpd", "Handler exception",e);
                //}
                if (mGpsLocationView != null) {
                    mGpsLocationView.setText("Lat: " + latitude + "\nLon: " + longitude);
                }
            }

            // @Override
            @Override
            public void onGpsNmeaUpdate(long time, String nmeaString) {
            }

            // @Override
            @Override
            public void onGpsStatusUpdate(
                    long time, int maxSatellites, int actualSatellites, int timeToFirstFix) {

            }
        });
    }

    public void startBalancer()
    {
        balancer.startBalancing(this);
    }

    public void stopBalancer()
    {
        balancer.stopBalancing(this);
    }

    public void stopToastTest() {
        mHandler.removeCallbacks(toastTest);
    };

    public void stopUpdateCompass() {
        mHandler.removeCallbacks(updateCompass);
    };

    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch)
    {
        if (seekBar.getId() == mBufferSensitivity.getId())
        {
            destBufferDist = (float) (25 * progress / 100);
            mDistBufferValueView.setText("Dist from waypoint sensitivity(m): " + destBufferDist);
        }
    }

    public void onStartTrackingTouch(SeekBar seekBar)
    {
    }

    public void onStopTrackingTouch(SeekBar seekBar)
    {
    }

    private void cleanup() {
        try {
            mGpsManager.shutdown();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // cleanup();
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
