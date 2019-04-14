package com.cab.mycab;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

// we are implementing some classes of google api and location to get some functions to update the
// location every second

// we will be using GeoFire api to save data in Firebase DB

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;

    private Button mlogout;

    // this will be th customerId of the request made
    private String customerId="";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mlogout = findViewById(R.id.logout);

        // function to logout using FireBase
        mlogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                // after sign out we go to MainActivity to elect driver or customer
                Intent intent = new Intent(DriverMapActivity.this,
                        MainActivity.class);
                startActivity(intent);
                finish();
                return;

            }
        });

        // as the name suggests for assigning customer to a driver
        getAssignedCustomer();
    }

    private void getAssignedCustomer(){
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Riders").child(driverId).child("customerRideId");

        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // this will get activated as soon as there is child created of Riders
                // dataSnapshot means an element of child
                if (dataSnapshot.exists()){
                    customerId = dataSnapshot.getValue().toString();
                    getAssignedCustomerPickupLocation();
                }
                // driver canceled request notice when driver is removed
                else {
                    customerId = "";
                    if (pickupMarker != null){
                        pickupMarker.remove();
                    }
                    if (assignedCustomerPickupLocationRefListener != null) {
                        assignedCustomerPickupLocationRef.removeEventListener(assignedCustomerPickupLocationRefListener);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });


    }

    // this function will get the pickup location of the customer who creates requests by storing
    // its lat ,log which will be assigned
    // we will call this function in getAssignedCustomer()

    // while removing the request we will remove the listeners and markers
    Marker pickupMarker;
    private DatabaseReference assignedCustomerPickupLocationRef;
    private ValueEventListener assignedCustomerPickupLocationRefListener;

    private void getAssignedCustomerPickupLocation(){
        assignedCustomerPickupLocationRef = FirebaseDatabase.getInstance().getReference()
                .child("CustomerRequest").child(customerId).child("l");

        assignedCustomerPickupLocationRefListener = assignedCustomerPickupLocationRef
                .addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && customerId.equals("")){
                    // we store in list because in database under a string id double type location
                    // is stored
                    List<Object> map =(List<Object>) dataSnapshot.getValue();

                    double locationLat = 0;
                    double locationLng = 0;

                    if (map.get(0) != null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if (map.get(1) != null){
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }

                    // storing the location of driver
                    LatLng driverLatLng = new LatLng(locationLat, locationLng);

                    // adding our marker to the map
                    pickupMarker = mMap.addMarker(new MarkerOptions()
                            .position(driverLatLng).title("Pickup Location")
                            .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));
                }

            }

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
       if (getApplicationContext() != null){

           mLastLocation = location;
           LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
           // to move the camera of maps to the location of user
           mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
           mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

           // adding the locations of driver to the database by creating DB dynamically
           String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

           DatabaseReference refAvailable = FirebaseDatabase.getInstance()
                   .getReference("DriversAvailable");
           GeoFire geoFireAvailable = new GeoFire(refAvailable);

           DatabaseReference refWorking = FirebaseDatabase.getInstance()
                   .getReference("DriversWorking");
           GeoFire geoFireWorking = new GeoFire(refWorking);


           switch (customerId){
               // if there is no request then no pickup shall be made
               case "":
                   // we set the location of available ones and remove the location of working ones
                   geoFireWorking.removeLocation(userId);

                   geoFireAvailable.setLocation(userId,
                           new GeoLocation(location.getLatitude(), location.getLongitude()));
                   break;

               default:
                   // if the above case is not happening then we do the opposite
                   geoFireAvailable.removeLocation(userId);

                   geoFireWorking.setLocation(userId,
                           new GeoLocation(location.getLatitude(), location.getLongitude()));
                   break;

           }







           // updating drivers location for customer
       }

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

    // to check when the driver is not logged in or not available

    @Override
    protected void onStop() {
        super.onStop();

        // when the driver logs out then we remove the location from the database
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("DriversAvailable");

        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);
    }
}
