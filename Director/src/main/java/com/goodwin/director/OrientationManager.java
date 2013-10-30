package com.goodwin.director;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.List;

/**
 * Android Orientation Sensor Manager Archetype
 * @author antoine vianey
 * under GPL v3 : http://www.gnu.org/licenses/gpl-3.0.html
 */
public class OrientationManager {
 
    private static Sensor sensor;
    private static SensorManager sensorManager;
    // you could use an OrientationListener array instead
    // if you plans to use more than one listener
    private static OrientationListener listener;
 
    /** indicates whether or not Orientation Sensor is supported */
    private static Boolean supported;
    /** indicates whether or not Orientation Sensor is running */
    private static boolean running = false;

    /** Finds the sensor number of the software-based orientation (orientation deprecated in android 4.2.2) */
    private static int sensorNumber;
    

    
    /** Sides of the phone */
    enum Side {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT;
    }
 
    /**
     * Returns true if the manager is listening to orientation changes
     */
    public static boolean isListening() {
        return running;
    }
 
    /**
     * Unregisters listeners
     */
    public static void stopListening() {
        running = false;
        try {
            if (sensorManager != null && sensorEventListener != null) {
                sensorManager.unregisterListener(sensorEventListener);
            }
        } catch (Exception e) {}
    }
 
    /**
     * Returns true if at least one Orientation sensor is available
     */
    public static boolean isSupported() {
        if (supported == null) {
          sensorManager = (SensorManager) DirectorActivity.sensorManager;
          if (sensorManager != null) {
                List<Sensor> sensors = sensorManager.getSensorList(
                        Sensor.TYPE_ALL);
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
        return supported;
    }
 
    /**
     * Registers a listener and start listening
     */
    public static void startListening(
            OrientationListener orientationListener) {
      sensorManager = (SensorManager) DirectorActivity.sensorManager;
      if (sensorManager != null) {
        List<Sensor> sensors = sensorManager.getSensorList(
                Sensor.TYPE_ALL);
        if (sensors.size() > 0) {
            sensor = sensors.get(sensorNumber);
            running = sensorManager.registerListener(
                    sensorEventListener, sensor, 
                    SensorManager.SENSOR_DELAY_FASTEST);
            listener = orientationListener;
        }
      }
    }
    
    /**
     * The listener that listen to events from the orientation listener
     */
    private static SensorEventListener sensorEventListener =
        new SensorEventListener() {
 
        /** The side that is currently up */
        private float azimuth = 0;
        private float pitch = 90;
        private float roll = 0;
        private long lastTimeStamp = System.nanoTime();
        private float pitchRate;
                
        
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
 
        public void onSensorChanged(SensorEvent event) {
 
            pitchRate = (pitch - event.values[1]) / (event.timestamp - lastTimeStamp);
          
            azimuth = event.values[0];     // azimuth
            pitch = event.values[1];     // pitch
            roll = event.values[2];        // roll
 
            // forwards orientation to the OrientationListener
            listener.onOrientationChanged(azimuth, pitch, roll,pitchRate);
        }
 
    };
}
