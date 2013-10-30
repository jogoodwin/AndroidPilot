package com.goodwin.director;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class Balancer implements OrientationListener
{

    DirectorActivity directorActivity;
    OrientationManager orientationManager;
    ResponseFunction responseFunction;
    private static SensorManager sensorManager;
    TextView leftPosText, rightPosText;
    CheckBox leftRev, rightRev;
    EditText desPitch, pitchTol, waitConst;

    String var;
    int maxLean = 3; // degrees
    int maxTurn = 5;
    int deadBand = 0;
    int pitchReverser = 1; // 1 means the reverser is NOT in action
    int azReverser = 1;
    int waitConstant = 900;
    int leftCenter, rightCenter, maxSpeed;
    int calibratorL,calibratorR,driftIndicatorR,driftIndicatorL = 0;
    int[] pulsePercent = {50,50,50};
    float channelWaitL, channelWaitR, responseTimeStartR, responseTimeR, responseTimeStartL, responseTimeL = 0;
    long lastTimestamp;
    float pitchHistory[], pitchDifferences[];
    float requestedPitch = (float) 0;
    float requestedAzimuth = (float) 0;
    float degPerSecond;

    boolean waitOverrideR, waitOverrideL = false;
    public static final String TAG = "BALANCER";

  public Balancer()
  {
    this.orientationManager = new OrientationManager();
  }

  public void startBalancing(DirectorActivity directorActivity/*, TextView rTextView, TextView lTextView */)
  {
    this.directorActivity = directorActivity;
    this.leftCenter = pulsePercent[0];
    this.rightCenter = pulsePercent[1];
    this.rightPosText = (TextView) directorActivity.findViewById(R.id.rightServoVal);
    this.leftPosText = (TextView) directorActivity.findViewById(R.id.leftServoVal);
    this.leftRev = directorActivity.leftRev;
    this.rightRev = directorActivity.rightRev;
    this.desPitch = (EditText) directorActivity.findViewById(R.id.pitchSet);
    this.pitchTol = (EditText) directorActivity.findViewById(R.id.pitchTol);
    this.waitConst = (EditText) directorActivity.findViewById(R.id.waitConst);

    if (leftRev.isChecked()) {
        azReverser = -1;
    }
    if (rightRev.isChecked()) {
        pitchReverser = -1;
    }
     try {
          requestedPitch = Float.parseFloat(desPitch.getText().toString());
     } catch (Exception e) {
          Toast.makeText(directorActivity.getBaseContext(),
                  "Error parsing requested pitch. Using default pitch of 1 degree.",
                  Toast.LENGTH_LONG).show();
     }
      try {
          maxLean = Integer.parseInt(pitchTol.getText().toString());
      } catch (Exception e) {
          Toast.makeText(directorActivity.getBaseContext(),
                  "Error parsing requested pitch tolerance. Using default pitch tolerance of 3 degrees.",
                  Toast.LENGTH_LONG).show();
      }
      try {
          waitConstant = Integer.parseInt(waitConst.getText().toString());
      } catch (Exception e) {
          Toast.makeText(directorActivity.getBaseContext(),
                  "Error parsing requested wait constant. Using default wait constant of 900.",
                  Toast.LENGTH_LONG).show();
      }

    if (Math.abs(leftCenter - 50) > Math.abs(leftCenter - 50))
    {
      maxSpeed = Math.abs(leftCenter - 50) - deadBand;
    }
    else
    {
      maxSpeed = Math.abs(rightCenter - 50) - deadBand;
    }

    if (OrientationManager.isSupported())
    {
      OrientationManager.startListening(this);
    } else {
        List<Sensor> sensors = directorActivity.sensorManager.getSensorList(
                Sensor.TYPE_ALL);
        for (int i = 0; i < sensors.size(); i++) {
            if (sensors.get(i).getName().equals("iNemoEngine Orientation sensor")) {
                var = "HIT";
            } else {
                var = sensors.get(i).getName();
            }
                Toast.makeText(
                        directorActivity.getBaseContext(),
                        var,
                        Toast.LENGTH_SHORT).show();
        }
    }

  }

  public void stopBalancing(DirectorActivity servoTester)
  {
    if (OrientationManager.isListening())
    {
      OrientationManager.stopListening();
    }

  }

  public void onOrientationChanged(float azimuth, float pitch, float roll, float timestamp)
  {
    // TODO Auto-generated method stub
    
    Float pitchError = pitchReverser * (requestedPitch - pitch); // thus -ive for +ive values of pitch
    Float azimuthError = (Math.abs(requestedAzimuth - azimuth) < 180) ? azReverser * (requestedAzimuth - azimuth) : azReverser * ((requestedAzimuth - azimuth) + 360);
    // Not required for now - maps 0 to 360 to -180 to 180
    // Math.signum(180 - azimuth)*(180-Math.abs(180-azimuth))
    leftCenter = pulsePercent[0];
    rightCenter = pulsePercent[1];
    //long curTime = SystemClock.uptimeMillis();
    byte[] leftByteArray = new byte[2];
    leftByteArray[0] = (byte) 190;
    byte[] rightByteArray = new byte[2];
    rightByteArray[0] = (byte) 191;

    /*for(int i = 100; i > 0; i--) {
        pitchHistory[i] = pitchHistory[i-1];
    }
        pitchHistory[0] = pitch;
    for (int i = 0; i < 100; i++) {
        pitchDifferences[i] = pitchHistory[i] - pitchHistory[i+1];
    }
    */
      // Would be good if a system response time could be coded in so start changing pulse, time taken for orientation to change
      
      // All that actually mattered for car was the most recent channel fired (leading to DC bias)
      // not the percent of each. Unless the phone keeps switching b/w positive and negative
      // channels really this can only control 2 channels. Need to combine signals + and - with
      // small time offset, which can then be fed through diodes to respective channels

      // Need improving to take acceleration into account so not constantly oscillating

      // Need individual waitConstant bc pitch needs to be more sensitive than azimuth

      if (SystemClock.uptimeMillis() > channelWaitL ) { // Override might be required for windy or rapidly changing conditions where wait has become obsolete
          if(azimuthError > maxTurn && leftCenter < 100)
          {
              // A floor effect in the calculation of setPulsePercent means that increases have to be larger than 2%
              pulsePercent[0] = leftCenter + 1;
              leftByteArray[1] = (byte) (leftCenter + 1);
              Log.d(TAG, directorActivity.byteArrayToIntString(leftByteArray));
              directorActivity.mConnectedThread.write(leftByteArray);
              float curTime = SystemClock.uptimeMillis();
              channelWaitL = (azimuthError <= 1 && azimuthError >= -1) ? curTime + waitConstant : curTime + (waitConstant / Math.abs(azimuthError));
              if (driftIndicatorL == 0) {
                  driftIndicatorL = 1;
                  calibratorL++;
                  responseTimeStartL = SystemClock.uptimeMillis();
              }
              // System inversely responsive - larger the error the less delay between setPulsePercent's
          } else if (azimuthError < -maxTurn && leftCenter > 0) {
              pulsePercent[0] = leftCenter - 1;
              leftByteArray[1] = (byte) (leftCenter - 1);
              Log.d(TAG, directorActivity.byteArrayToIntString(leftByteArray));
              directorActivity.mConnectedThread.write(leftByteArray);
              float curTime = SystemClock.uptimeMillis();
              channelWaitL = (azimuthError <= 1 && azimuthError >= -1) ? curTime + waitConstant : curTime + (waitConstant / Math.abs(azimuthError));
              if (driftIndicatorL == 0) {
                  driftIndicatorL = -1;
                  calibratorL--;
                  responseTimeStartL = SystemClock.uptimeMillis();
              }
          } else if (Math.abs(azimuthError) <= maxTurn && Math.abs(azimuthError) >=-maxTurn) {
              pulsePercent[0] = 50 + calibratorL;
              leftByteArray[1] = (byte) (50 + calibratorL);
              Log.d(TAG, directorActivity.byteArrayToIntString(leftByteArray));
              directorActivity.mConnectedThread.write(leftByteArray);
              driftIndicatorL = 0;
              float curTime = SystemClock.uptimeMillis();
              channelWaitL = (azimuthError <= 1 && azimuthError >= -1) ? curTime + waitConstant : curTime + (waitConstant / Math.abs(azimuthError));
              responseTimeL = SystemClock.uptimeMillis() - responseTimeStartL;
              // This is where derivative and response time code can go
          }
      }

      // Probably should create general form for general channels
      if (SystemClock.uptimeMillis() > channelWaitR ) { // Override might be required for windy or rapidly changing conditions where wait has become obsolete
          if(pitchError > maxLean && rightCenter < 100)
          {
              // A floor effect in the calculation of setPulsePercent means that increases have to be larger than 2%
              pulsePercent[1] = rightCenter + 1;
              rightByteArray[1] = (byte) (rightCenter + 1);
              Log.d(TAG, directorActivity.byteArrayToIntString(rightByteArray));
              directorActivity.mConnectedThread.write(rightByteArray);
              float curTime = SystemClock.uptimeMillis();
              channelWaitR = (pitchError <= 1 && pitchError >= -1) ? curTime + waitConstant : curTime + (waitConstant / Math.abs(pitchError));
              if (driftIndicatorR == 0) {
                  driftIndicatorR = 1;
                  calibratorR++;
                  responseTimeStartR = SystemClock.uptimeMillis();
              }
              // System inversely responsive - larger the error the less delay between setPulsePercent's
          } else if (pitchError < -maxLean && rightCenter >= 0) {
              pulsePercent[1] = rightCenter - 1;
              rightByteArray[1] = (byte) (rightCenter - 1);
              Log.d(TAG, directorActivity.byteArrayToIntString(rightByteArray));
              directorActivity.mConnectedThread.write(rightByteArray);
              float curTime = SystemClock.uptimeMillis();
              channelWaitR = (pitchError <= 1 && pitchError >= -1) ? curTime + waitConstant : curTime + (waitConstant / Math.abs(pitchError));
              if (driftIndicatorR == 0) {
                  driftIndicatorR = -1;
                  calibratorR--;
                  responseTimeStartR = SystemClock.uptimeMillis();
              }
          } else if (Math.abs(pitchError) < maxLean) {
              pulsePercent[1] = 50 + calibratorR;
              rightByteArray[1] = (byte) (50 + calibratorR);
              Log.d(TAG, directorActivity.byteArrayToIntString(rightByteArray));
              directorActivity.mConnectedThread.write(rightByteArray);
              float curTime = SystemClock.uptimeMillis();
              channelWaitR = (pitchError <= 1 && pitchError >= -1) ? curTime + waitConstant : curTime + (waitConstant / Math.abs(pitchError));
              driftIndicatorR = 0;
              responseTimeR = (responseTimeStartR > 0) ? SystemClock.uptimeMillis() - responseTimeStartR : 0;
              // This is where derivative and response time code can go
          }
      }
      //rightPosText.setText("Servo 4(R Neg) Pulse width = " + pulseGenerator.getPulseMs(3) + "ms (" + pulseGenerator.getPulseSamples(3) + " samples)");
      rightPosText.setText("AzimuthError = " + azimuthError + "; ReqAzimuth = " + requestedAzimuth + "; Azimuth = " + azimuth);
  }

    // This is going to suffer from passing an array by reference
    /*private void positiveErrorResponse(PulseGenerator pulseGen, byte[] byteArray, int center, int channel, float channelWait,
                                       Float error, int driftIndicator, int calibrator, float responseTimeStart){
        // A floor effect in the calculation of setPulsePercent means that increases have to be larger than 2%
        if (channel == 0) {
            pulseGen.setPulsePercent(center + 3, 1);
        } else {
            pulseGen.setPulsePercent(center + 3, 3);
        }
        byteArray[1] = (byte) (center + 3);
        Log.d(TAG,directorActivity.byteArrayToIntString(byteArray));
        directorActivity.mConnectedThread.write(byteArray);
        float curTime = SystemClock.uptimeMillis();
        channelWait = (error <= 1 && error >= -1) ? curTime + waitConstant : curTime + (waitConstant / Math.abs(error));
        if (driftIndicator == 0) {
            driftIndicator = 1;
            calibrator++;
            responseTimeStart = SystemClock.uptimeMillis();
        }
    }*/

    private void negativeErrorResponse(){

    }

    private void acceptableErrorResponse() {

    }
}
