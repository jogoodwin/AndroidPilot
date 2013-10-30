package com.goodwin.director;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/*
 * MyMapActivity class forms part of Map application
 * in Mobiletuts+ tutorial series:
 * Using Google Maps and Google Places in Android Apps
 * 
 * This version of the class is for Part 2 of the series.
 * 
 * Sue Smith
 * March 2013
 */

// 56:ED:7A:6A:EC:6F:6D:14:9A:E5:AC:F9:C2:23:4D:22:41:F5:1D:E1;com.example.mapping
public class MappingActivity extends Activity {

    //instance variables for Marker icon drawable resources
    private int userIcon, foodIcon, drinkIcon, shopIcon, otherIcon;
    private TextView changeButton;
    //the map
    private GoogleMap theMap;

    //location manager
    private LocationManager locMan;

    //user marker
    private Marker userMarker;
    //Path to travel
    private Polyline wayPointLine;
    private List<Marker> polyLineMarkers = new ArrayList<Marker>();
    private static String TAG = "WAYPOINT MAPPING";
    private ArrayList<ArrayList<Double>> wayPoints = new ArrayList<ArrayList<Double>>();
    //private Intent returnIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.mapping);

        findViewById(R.id.exitProfileImage).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent returnIntent = new Intent();
                List<LatLng> storedWayPoints = wayPointLine.getPoints();
                //storedWayPoints.convertLatLng(wayPointLine.getPoints());
                for (LatLng point : storedWayPoints) {
                    ArrayList<Double> x = new ArrayList<Double>();
                    x.add(point.latitude);
                    x.add(point.longitude);
                    wayPoints.add(x);
                }
                Bundle bundle = new Bundle();
                bundle.putSerializable("LatLngString",wayPoints);
                returnIntent.putExtras(bundle);
                setResult(RESULT_OK,returnIntent);
                finish();
            }
        });

        changeButton = (TextView) findViewById(R.id.customtitlebar);
        changeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (changeButton.getText() == "Edit Line") {
                    changeButton.setText("Return To Line");
                    convertPointsToMarkers();
                } else {
                    changeButton.setText("Edit Line");
                    convertMarkersToPoints();
                }
            }
        });
        //get drawable IDs
        userIcon = R.drawable.blue_point;
        /*foodIcon = R.drawable.red_point;
        drinkIcon = R.drawable.blue_point;
        shopIcon = R.drawable.green_point;
        otherIcon = R.drawable.purple_point;*/

        //find out if we already have it
        if(theMap==null){
            //get the map
            theMap = ((MapFragment)getFragmentManager().findFragmentById(R.id.the_map)).getMap();
            //check in case map/ Google Play services not available
            if(theMap!=null){
                //ok - proceed
                theMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                //update location
                updatePlaces();
                theMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(LatLng latLng) {
                        if (wayPointLine != null) {
                            List<LatLng> updatedPoints = wayPointLine.getPoints();
                            updatedPoints.add(latLng);
                            wayPointLine.setPoints(updatedPoints);
                        } else if (userMarker!=null){
                        PolylineOptions polOptions = new PolylineOptions()
                                .add(userMarker.getPosition())
                                .add(latLng);
                        userMarker.remove();
                        wayPointLine = theMap.addPolyline(polOptions);
                        /*theMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .draggable(true)
                        .icon(BitmapDescriptorFactory.fromResource(userIcon)));*/
                        }
                    }
                }

                );
            }

        }
    }

    public class PackageLatLng implements Serializable {
        private List<LatLng> storedList;

        public void convertLatLng (List<LatLng> latLngList) {
            this.storedList = latLngList;
        }

        public List<LatLng> getStoredList() {
            return this.storedList;
        }
    }

    private void updatePlaces(){
        //get location manager
        locMan = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        //get last location
        Location lastLoc = locMan.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        double lat = lastLoc.getLatitude();
        double lng = lastLoc.getLongitude();
        //create LatLng
        LatLng lastLatLng = new LatLng(lat, lng);

        //remove any existing marker
        if(userMarker!=null) userMarker.remove();
        //create and set marker properties
        userMarker = theMap.addMarker(new MarkerOptions()
                .position(lastLatLng)
                        //.title("You are here")
                .icon(BitmapDescriptorFactory.fromResource(userIcon))
                //.snippet("Your last recorded location")
        );
        //move to location
        theMap.animateCamera(CameraUpdateFactory.newLatLng(lastLatLng), 3000, null);
    }

    private void convertPointsToMarkers() {
        if (wayPointLine != null) {
            List<LatLng> currentPoints = wayPointLine.getPoints();
            wayPointLine.setVisible(false);
            for (LatLng point : currentPoints) {
                try {
                    polyLineMarkers.add(theMap.addMarker(new MarkerOptions()
                            .position(point)
                            .icon(BitmapDescriptorFactory.fromResource(userIcon))
                            .draggable(true)
                    )
                    );
                } catch (NullPointerException e) {
                    Log.e(TAG, "Conversion to Markers failed.");
                }
            }
        }
    }

    private void convertMarkersToPoints() {
        if (polyLineMarkers != null && wayPointLine != null) {
            if (!polyLineMarkers.isEmpty()) {
                List<LatLng> currentPoints = new ArrayList<LatLng>();
                for (Marker m: polyLineMarkers) {
                    currentPoints.add(m.getPosition());
                    m.remove();
                }
                polyLineMarkers.clear();
                wayPointLine.setPoints(currentPoints);
                wayPointLine.setVisible(true);
            }
        }
    }

}