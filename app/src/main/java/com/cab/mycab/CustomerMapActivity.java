package com.cab.mycab;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;


public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;

    // the  logout and request button
    private Button mLogout, mRequest;

    // variable to store location
    private LatLng pickupLocation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mLogout = findViewById(R.id.logout);
        mRequest = findViewById(R.id.request);

        // function to logout using FireBase
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                // after sign out we go to MainActivity to elect driver or customer
                Intent intent = new Intent(CustomerMapActivity.this,
                        MainActivity.class);
                startActivity(intent);
                finish();
                return;

            }
        });

        // function to call the cab
        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                // this will create a database named as mentioned to store the request
                // done by the user
                DatabaseReference ref = FirebaseDatabase.getInstance().
                        getReference("CustomerRequest");

                // creating new instance of GeoFire for request DB
                GeoFire geoFire = new GeoFire(ref);
                geoFire.setLocation(userId,
                        new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));

                // we store the location
                pickupLocation =
                        new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());

                // now we add a marker
                mMap.addMarker(new MarkerOptions().position(pickupLocation).title("Pickup Here"));

                // after a successful request we change the text of the request button
                mRequest.setText("Getting Driver...");

                // we call the function to get drivers
                getClosestDriver();
            }
        });
    }

    // variables needed for the function getClosestDriver()
    private int radius =1;
    private Boolean driverFound = false;
    private String driverFoundId;

    // create a function to fetch the closest drivers available for a request generated
    public void getClosestDriver(){
        DatabaseReference driverLocation = FirebaseDatabase.getInstance()
                .getReference().child("DriversAvailable");
        GeoFire geoFire = new GeoFire(driverLocation);

        // creating GeoFire Queries to access drivers at 1 km radius from the request location
        GeoQuery geoQuery = geoFire.
                queryAtLocation(new GeoLocation(pickupLocation.latitude, pickupLocation.longitude),
                        radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            // this function will work when a driver is found
            // so once we get in the function we assume that a driver is found ,
            // so we set Boolean value to true
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!driverFound){
                    driverFound = true;
                    driverFoundId=key;

                    // to inform the driver found that a request is pending we create a child of
                    // Rider table. With this the driver will get to know the customer
                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference()
                            .child("Users").child("Riders").child(driverFoundId);
                    // we put the customerId inside the Rider table
                    String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    HashMap map = new HashMap();
                    map.put("customerRideId", customerId);
                    driverRef.updateChildren(map);

                    // to show the driver location on Customer Map we create this function
                    getDriverLocation();
                    mRequest.setText("Locating Driver...");
                }

            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                // we keep on increasing the radius till the driver is found
                if (!driverFound){
                    radius++;
                    // recursive calling
                    getClosestDriver();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private Marker mDriverMarker;
    // function to locate the driver for customer
    private void getDriverLocation(){
        DatabaseReference driverLocationRef = FirebaseDatabase.getInstance().getReference()
                .child("DriversWorking").child(driverFoundId).child("l");
        driverLocationRef.addValueEventListener(new ValueEventListener() {
            // every time the location changes this function will be called
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()){
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;
                    mRequest.setText("Driver Found...");
                    if (map.get(0) != null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if (map.get(1) != null){
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }

                    // string the location of driver
                    LatLng driverLatLng = new LatLng(locationLat, locationLng);

                    // if there is marker but no driver found just in case
                    if (mDriverMarker != null){
                        mDriverMarker.remove();
                    }

                    // adding our marker to the map
                    mDriverMarker = mMap.addMarker(new MarkerOptions()
                            .position(driverLatLng).title("Your Driver"));
                }
            }

            // we wont be using this function
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);
    }

    protected synchronized void buildGoogleApiClient() {
        // here we create the API
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        // allows to use the API
        mGoogleApiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        // to move the camera of maps to the location of user
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        // this function will work when we are connected to the map
        mLocationRequest = new LocationRequest();
        // we set this to update the location every 1 sec
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        // to control the usage of battery while updating for location
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // FuseLocationApi is deprecated so we may have to use FusedApiProviderClient later on
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    // to check when the Customer is not logged in or not available

    @Override
    protected void onStop() {
        super.onStop();


    }
}

