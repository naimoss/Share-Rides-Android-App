package com.example.abhishek.sharerides.activities;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.example.abhishek.sharerides.R;
import com.example.abhishek.sharerides.helpers.Utils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.parse.FindCallback;
import com.parse.LogOutCallback;
import com.parse.ParseACL;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import com.parse.SaveCallback;

import java.util.List;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class DashboardActivity extends AppCompatActivity implements
        NavigationView.OnNavigationItemSelectedListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        GoogleMap.OnInfoWindowClickListener {

    public ProgressDialog progressDialog;

    private SupportMapFragment mapFragment;
    private GoogleMap map;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private long UPDATE_INTERVAL = 60000;
    private long FASTEST_INTERVAL = 5000;

    private MarkerOptions markerOptions;
    private LatLng latLng;
    private boolean requestActive = false;
    private String driverUsername = "";
    private String showWaitTime = "";
    ParseGeoPoint driverLocation = new ParseGeoPoint(0,0);
    private LatLng driverLoc;


    /**
	 * Define a request code to send to Google Play services This code is
	 * returned in Activity.onActivityResult
	 */
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;


    /**
     * Called when the activity is visible initially
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Top Bar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Drawer from the left to right with action items
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        toggle.syncState();

        // Navigation Items (Settings, SignOut)
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Creating the instance for the Map Fragment
        mapFragment = SupportMapFragment.newInstance();


        if (mapFragment != null) {
            android.support.v4.app.FragmentManager sfm = getSupportFragmentManager();
            sfm.beginTransaction().add(R.id.riderMap, mapFragment).commit();

            // Loading Map in the Map Fragment
            mapFragment.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap map) {
                    loadMap(map);
                }
            });

        } else {

            Toast.makeText(this, "Error - Map Fragment was null!!", Toast.LENGTH_SHORT).show();
        }

        if(requestActive == false) {
            ParseQuery<ParseObject> query = ParseQuery.getQuery("Requests");
            query.whereEqualTo("requesterUsername", ParseUser.getCurrentUser().getUsername());
            query.findInBackground(new FindCallback<ParseObject>() {
                @Override
                public void done(List<ParseObject> objects, ParseException e) {
                    if(e == null) {

                        if(objects.size() > 0) {

                            for(ParseObject object : objects) {

                                requestActive = true;

                                if(object.get("driverUsername") != null) {

                                    driverUsername = object.getString("driverUsername");

                                }

                            }

                        }

                    }
                }
            });
        }
    }

    /**
     * Load Google Map
     * @param googleMap
     */
    protected void loadMap(GoogleMap googleMap) {
        map = googleMap;
        if (map != null) {
            // Map is ready
            Toast.makeText(this, "Map Fragment was loaded properly!", Toast.LENGTH_SHORT).show();
            DashboardActivityPermissionsDispatcher.getMyLocationWithCheck(this);



        } else {
            Toast.makeText(this, "Error - Map was null!!", Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * Permission Result
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        DashboardActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }


    /**
     * Set user's current location
     */
    @SuppressWarnings("all")
    @NeedsPermission({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void getMyLocation() {
        if (map != null) {
            // Now that map has loaded, let's get our location!
            map.setMyLocationEnabled(true);
            map.setOnInfoWindowClickListener(this);
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this).build();
            connectClient();
        }
    }


    /**
     * Check for the GooglePlayServices availability and try to connect to GoogleAPI Client
     */
    protected void connectClient() {
        // Connect the client.
        if (isGooglePlayServicesAvailable() && mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }


    /**
     * Called when the Activity becomes visible.
     */
    @Override
    protected void onStart() {
        super.onStart();
        connectClient();
    }


    /**
	 * Called when the Activity is no longer visible.
	 */
    @Override
    protected void onStop() {

        // Disconnecting the client invalidates it.
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }


    /**
	 * Handle results returned to the FragmentActivity by Google Play services
	 */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // Decide what to do based on the original request code
        switch (requestCode) {

            case CONNECTION_FAILURE_RESOLUTION_REQUEST:

			/*
			 * If the result code is Activity.RESULT_OK, try to connect again
			 */
            switch (resultCode) {
                case Activity.RESULT_OK:
                    mGoogleApiClient.connect();
                    break;
            }

        }
    }


    /**
     * Verify whether GooglePlayServices is available
     * @return
     */
    private boolean isGooglePlayServicesAvailable() {

        // Check that Google Play services is available
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            // In debug mode, log the status
            Log.d("Location Updates", "Google Play services is available.");
            return true;

        } else {

            // Get the error dialog from Google Play services
            Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                    CONNECTION_FAILURE_RESOLUTION_REQUEST);

            // If Google Play services can provide an error dialog
            if (errorDialog != null) {

                // Create a new DialogFragment for the error dialog
                ErrorDialogFragment errorFragment = new ErrorDialogFragment();
                errorFragment.setDialog(errorDialog);
                errorFragment.show(getSupportFragmentManager(), "Location Updates");
            }

            return false;
        }
    }


    /**
	 * Called by Location Services when the request to connect the client
	 * finishes successfully. At this point, you can request the current
	 * location or start periodic updates
	 */
    @Override
    public void onConnected(Bundle dataBundle) {
        // Display the connection status
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (location != null) {
            Toast.makeText(this, "GPS location was found!", Toast.LENGTH_SHORT).show();

            // Getting user's current location
            latLng = new LatLng(location.getLatitude(), location.getLongitude());

            // Zooming
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 17);

            // Taking user to its location with animate camera
            map.animateCamera(cameraUpdate);

            // Setting up marker
            setUpMarker(Utils.SET_PICKUP_LOCATION);

        } else {

            Toast.makeText(this, "Current location was null, enable GPS on emulator!", Toast.LENGTH_SHORT).show();

        }


        startLocationUpdates();
    }

    /**
     *
     * Set Location Updates
     */
    protected void startLocationUpdates() {

        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                mLocationRequest, this);

    }

    /**
     * Called when the location is changed depending on the interval
     * @param location
     */
    public void onLocationChanged(Location location) {
        // Report to the UI that the location was updated
        String msg = "Updated Location: " +
                Double.toString(location.getLatitude()) + "," +
                Double.toString(location.getLongitude());
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

        latLng = new LatLng(location.getLatitude(), location.getLongitude());

        // Zooming
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 17);

        // Taking user to its location with animate camera
        map.animateCamera(cameraUpdate);

        // Setting up marker
        setUpMarker(Utils.SET_PICKUP_LOCATION);
    }


    /**
     * Called by Location Services if the connection to the location client
     * drops because of an error.
     */
    @Override
    public void onConnectionSuspended(int i) {
        if (i == CAUSE_SERVICE_DISCONNECTED) {
            Toast.makeText(this, "Disconnected. Please re-connect.", Toast.LENGTH_SHORT).show();
        } else if (i == CAUSE_NETWORK_LOST) {
            Toast.makeText(this, "Network lost. Please re-connect.", Toast.LENGTH_SHORT).show();
        }
    }



    /**
	 * Called by Location Services if the attempt to Location Services fails.
	 */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
		/*
		 * Google Play services can resolve some errors it detects. If the error
		 * has a resolution, try sending an Intent to start a Google Play
		 * services activity that can resolve error.
		 */
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this,
                        CONNECTION_FAILURE_RESOLUTION_REQUEST);
				/*
				 * Thrown if Google Play services canceled the original
				 * PendingIntent
				 */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        } else {

            Toast.makeText(getApplicationContext(),
                    "Sorry. Location services not available to you", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Define a DialogFragment that displays the error dialog
     */
    public static class ErrorDialogFragment extends DialogFragment {

        // Global field to contain the error dialog
        private Dialog mDialog;

        // Default constructor. Sets the dialog field to null
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }

        // Set the dialog to display
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }

        // Return a Dialog to the DialogFragment.
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }


    /**
     * Called when the user click on the device back button
     */
    @Override
    public void onBackPressed() {

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        if (drawer.isDrawerOpen(GravityCompat.START)) {

            drawer.closeDrawer(GravityCompat.START);

        } else {

            super.onBackPressed();

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /**
     * Called when the user click on the Navigation Item
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.action_signout) {
            signOff();
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * DrawerLayout Items List
     * Add func to handle it
     * @param item
     * @return
     */
    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Logging out user action
     */
    public void signOff() {
        showCustomProgress(true, Utils.LOGOUT);

        ParseUser.logOutInBackground(new LogOutCallback() {
            @Override
            public void done(ParseException e) {
                if (e == null) {

                    Intent backToLogin = new Intent(getApplicationContext(), MainActivity.class);
                    showCustomProgress(false, Utils.LOGOUT);
                    startActivity(backToLogin);

                    finish();
                }
            }
        });
    }


    /**
     * Shows the custom progress UI
     * @param show
     * @param message
     */
    private void showCustomProgress(boolean show, String message) {
        if (show) {
            progressDialog = new ProgressDialog(DashboardActivity.this, R.style.AppTheme_Dark_Dialog);
            progressDialog.setIndeterminate(true);
            progressDialog.setMessage(message);
            progressDialog.setCancelable(false);
            progressDialog.show();
        } else {
            progressDialog.dismiss();
        }
    }


    /**
     * Called when the user click on the Marker's Info Window
     * @param marker
     */
    @Override
    public void onInfoWindowClick(Marker marker) {

        Toast.makeText(getApplicationContext(),
                "Info Window clicked@ At" + marker.getTitle(),
                Toast.LENGTH_SHORT).show();

        String action = marker.getTitle();

        if(!action.equals(Utils.CANCEL_RIDE)) {

            requestActive = true;

            // Call func ff the user request for the ride
            sendDriverRequest();

        } else {

            requestActive = false;
            // Call func if the user wants to cancel the ride request
            cancelDriverRequest();

        }

    }


    /**
     *  Send Driver's Request to Parse API
     *
     */
    private void sendDriverRequest() {

        showCustomProgress(true, Utils.SEARCHING_DRIVER);


        // Calling Parse API for storing rider info
        ParseObject request = new ParseObject("Requests");
        request.put("requesterUsername", ParseUser.getCurrentUser().getUsername());

        // Setting Access Control List on Parse
        ParseACL parseACL = new ParseACL();
        parseACL.setPublicWriteAccess(true);
        parseACL.setPublicReadAccess(true);
        request.setACL(parseACL);


        // Saving operation in background
        request.saveInBackground(new SaveCallback() {
            @Override
            public void done(ParseException e) {
                if(e == null) {

                    // Update Rider Location
                    updateRiderLocationToParse();

                    // Setup the marker
                    setUpMarker(Utils.CANCEL_RIDE);

                    // Dismiss the custom dialog
                    showCustomProgress(false, Utils.SEARCHING_DRIVER);
                }
            }
        });
    }

    /**
     * Update Rider's Current Location to Parse
     *
     */
    private void updateRiderLocationToParse() {


        // Passing Rider's location to Parse
        final ParseGeoPoint riderLocation = new ParseGeoPoint(latLng.latitude, latLng.longitude);

        // Calling Parse API for Getting and Deleting Data
        ParseQuery<ParseObject> query = new ParseQuery<ParseObject>("Requests");
        query.whereEqualTo("requesterUsername", ParseUser.getCurrentUser().getUsername());

        query.findInBackground(new FindCallback<ParseObject>() {
            @Override
            public void done(List<ParseObject> objects, ParseException e) {

                if(e == null)
                {
                    if(objects.size() > 0)
                    {
                        for (ParseObject obj : objects)
                        {
                            // Parse - Saving
                            obj.put("requesterLocation", riderLocation);
                            obj.saveInBackground();
                        }
                    }
                }

            }
        });

    }


    /**
     * Cancel Driver's Request to Parse API by deleting the request
     *
     */
    private void cancelDriverRequest() {

        // Show the custom dialog
        showCustomProgress(true, Utils.CANCELLING_REQUEST);

        // Calling Parse API for Getting and Deleting Data
        ParseQuery<ParseObject> query = new ParseQuery<ParseObject>("Requests");
        query.whereEqualTo("requesterUsername", ParseUser.getCurrentUser().getUsername());

        query.findInBackground(new FindCallback<ParseObject>() {
            @Override
            public void done(List<ParseObject> objects, ParseException e) {

                if(e == null)
                {
                    // Dismiss the custom dialog
                    showCustomProgress(false, Utils.CANCELLING_REQUEST);

                    // Setup the marker
                    setUpMarker(Utils.SET_PICKUP_LOCATION);

                    if(objects.size() > 0)
                    {
                        for (ParseObject obj : objects)
                        {
                            // Parse -  Deleting in background
                            obj.deleteInBackground();
                        }
                    }
                }

            }
        });

    }





    /**
     * Shows marker with the custom title
     * @param title
     */
    private void setUpMarker(String title) {

        if(!requestActive){

            markerOptions = new MarkerOptions();
            map.clear();
            markerOptions.position(latLng);
            markerOptions.title(title);
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker());
            map.addMarker(markerOptions).showInfoWindow();

        } else {

            if(!driverUsername.equals("")){

                ParseQuery<ParseUser> userQuery = ParseUser.getQuery();
                userQuery.whereEqualTo("username", driverUsername);
                userQuery.findInBackground(new FindCallback<ParseUser>() {
                    @Override
                    public void done(List<ParseUser> objects, ParseException e) {
                        if(e == null){

                            if(objects.size() > 0) {

                                for(ParseUser driver : objects) {

                                    driverLocation = driver.getParseGeoPoint("location");


                                }

                            }

                        }

                    }
                });

                if(driverLocation.getLatitude() != 0 && driverLocation.getLongitude() != 0){

                    Log.d("Appinfo", driverLocation.toString());

                    Double distanceInMiles = driverLocation.distanceInMilesTo(new ParseGeoPoint(latLng.latitude, latLng.longitude));
                    Double distanceOneDP  = (double) Math.round(distanceInMiles * 10) / 10;

                   // Todo : Needs to calculate time from the speed
                    Double distance = distanceOneDP;
                    double speed = 30.0d; // kmph

                    double speed_in_meters_per_minute = ( speed * 1000 ) / distance; // mpm

                    // now calculate time in minutes
                    double time = (double)distance / speed_in_meters_per_minute ;


                    Log.d("Test", String.valueOf(time));

                    showWaitTime = "Your driver is";

                }

                map.clear();

                markerOptions = new MarkerOptions();
                markerOptions.position(latLng);
                markerOptions.title(showWaitTime);
                markerOptions.icon(BitmapDescriptorFactory.defaultMarker());
                map.addMarker(markerOptions).showInfoWindow();

                driverLoc = new LatLng(driverLocation.getLatitude(), driverLocation.getLongitude());
                map.addMarker(new MarkerOptions()
                    .position(driverLoc)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

                /*
                 * Showing all markers on the map
                 */
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                builder.include(driverLoc);
                builder.include(latLng);
                LatLngBounds bounds = builder.build();
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));

            }

        }


    }

}
