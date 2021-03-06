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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
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
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.*;

// we will show the driver info only after we get the closest driver
public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;

    SupportMapFragment mapFragment;

    // the  logout, request and settings button
    private Button mLogout, mRequest, mSettings, mHistory;

    // variable to store location
    private LatLng pickupLocation;

    // this variable will be used for cancelling request
    private Boolean requestBol= false;

    private Marker pickupMarker;

    // we will use this for place autocomplete api and create DataBase content
    private String destination = " ", requestService;

    // we will use this for storing in database to create route
    private LatLng destinationLatLng;

    // variables to show customer info
    private TextView mDriverName, mDriverPhone, mDriverCar;
    // these are the variables for our customer profile when the request is received
    private LinearLayout mDriverInfo;

    private ImageView mDriverImage;

    private RadioGroup mServicesRadioGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        // double checking for permissions
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CustomerMapActivity.this,
                    new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }
        else {
            mapFragment.getMapAsync(this);
        }


        // buttons
        mLogout = findViewById(R.id.logout);
        mRequest = findViewById(R.id.request);
        mSettings = findViewById(R.id.settings);
        mHistory = findViewById(R.id.history);

        //variables for driver info
        mDriverInfo = findViewById(R.id.driverInfo);
        mDriverImage = findViewById(R.id.driverProfileImage);
        mDriverName = findViewById(R.id.driverName);
        mDriverPhone = findViewById(R.id.driverPhone);
        mDriverCar= findViewById(R.id.driverCar);

        mServicesRadioGroup = findViewById(R.id.servicesRadioGroup);
        mServicesRadioGroup.check(R.id.CabX);

        // initializing with default values
        destinationLatLng = new LatLng(0.0,0.0);

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
        // also in this function we will be adding the codes to cancel the request
        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // we will be removing all the listeners and also remove all the requests associated
                // from the DB
                if (requestBol){
                   endRide();
                }
                else {
                    // for each radio button we give an ID
                    int selectId = mServicesRadioGroup.getCheckedRadioButtonId();
                    final RadioButton radioButton = findViewById(selectId);
                    if (radioButton.getText() == null){
                        return;
                    }
                    requestService = radioButton.getText().toString();
                    requestBol = true;

                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    // this will create a database named as mentioned to store the request
                    // done by the user
                    DatabaseReference ref = FirebaseDatabase.getInstance().
                            getReference("customerRequest");

                    // creating new instance of GeoFire for request DB
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.setLocation(userId,
                            new GeoLocation(mLastLocation.getLatitude(),
                                    mLastLocation.getLongitude()));

                    // we store the location
                    pickupLocation =
                            new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());

                    // now we add a marker
                    pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLocation)
                            .title("Pickup Here")
                            .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));

                    // after a successful request we change the text of the request button
                    mRequest.setText("Getting Driver...");

                    // we call the function to get drivers
                    getClosestDriver();
                }
            }
        });

        // functions of settings button
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CustomerMapActivity.this,
                        CustomerSettingsActivity.class);
                startActivity(intent);
                return;
            }
        });

        // functions of history button
        mHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CustomerMapActivity.this,
                        HistoryActivity.class);
                intent.putExtra("customerOrDriver", "Customers");
                startActivity(intent);
                return;
            }
        });

        // procedure for place automation
        // we will use the destination variable
        // Initialize the AutocompleteSupportFragment.
        AutocompleteSupportFragment autocompleteFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        // Set up a PlaceSelectionListener to handle the response.
        // this will be used to handle the destination place
        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                // TODO: Get info about the selected place.
                destination = place.getName().toString();
                // we have saved the destination point
                destinationLatLng = place.getLatLng();
            }

            @Override
            public void onError(Status status) {
                // TODO: Handle the error.
            }
        });
    }

    // variables needed for the function getClosestDriver()
    private int radius =1;
    private Boolean driverFound = false;
    private String driverFoundId;

    GeoQuery geoQuery;

    // create a function to fetch the closest drivers available for a request generated
    public void getClosestDriver(){
        DatabaseReference driverLocation = FirebaseDatabase.getInstance()
                .getReference()
                .child("DriversAvailable");
        GeoFire geoFire = new GeoFire(driverLocation);

        // creating GeoFire Queries to access drivers at 1 km radius from the request location
        geoQuery = geoFire.
                queryAtLocation(new GeoLocation(pickupLocation.latitude, pickupLocation.longitude),
                        radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            // this function will work when a driver is found
            // so once we get in the function we assume that a driver is found ,
            // so we set Boolean value to true
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!driverFound && requestBol){
                    // to check for service request of customer
                    DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance()
                            .getReference().child("Users").child("Riders").child(key);
                    mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0){
                                Map<String, Object> driverMap =
                                        (Map<String, Object>) dataSnapshot.getValue();
                                if (driverFound){
                                    return;
                                }
                                // requestService holds the service selected by the user
                                if (driverMap.get("service").equals(requestService)){
                                    driverFound = true;
                                    driverFoundId = dataSnapshot.getKey();

                                    // to inform the driver found that a request is pending
                                    // we create a child of
                                    // Rider table.With this the driver will get to know customer
                                    DatabaseReference driverRef =
                                            FirebaseDatabase.getInstance().getReference()
                                            .child("Users")
                                            .child("Riders")
                                            .child(driverFoundId)
                                            .child("customerRequest");
                                    // we put the customerId inside the Rider table
                                    String customerId =
                                            FirebaseAuth.getInstance().getCurrentUser().getUid();
                                    HashMap map = new HashMap();
                                    map.put("customerRideId", customerId);
                                    map.put("destination", destination);

                                    // we store the destination address in the database
                                    // to be used for creating destination route
                                    map.put("destinationLat", destinationLatLng.latitude);
                                    map.put("destinationLng", destinationLatLng.longitude);
                                    driverRef.updateChildren(map);

                                    // to show driver location on Customer Map we create function
                                    getDriverLocation();
                                    // we will show the driver info after we get closest driver
                                    getDriverInfo();
                                    // when ride ends
                                    getHasRideEnded();
                                    mRequest.setText("Locating Driver...");
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                        }
                    });
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
    private DatabaseReference driverLocationRef;
    private ValueEventListener driverLocationRefListener;
    // function to locate the driver for customer
    private void getDriverLocation(){
        driverLocationRef = FirebaseDatabase.getInstance().getReference()
                .child("DriversWorking").child(driverFoundId).child("l");
        driverLocationRefListener = driverLocationRef.addValueEventListener(new ValueEventListener() {
            // every time the location changes this function will be called
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && requestBol){
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

                    // to show the distance between the driver and customer

                    // this has the location of pickup
                    Location loc1 = new Location("");
                    loc1.setLatitude(pickupLocation.latitude);
                    loc1.setLongitude(pickupLocation.longitude);

                    // this has the location of driver
                    Location loc2 = new Location("");
                    loc2.setLatitude(driverLatLng.latitude);
                    loc2.setLongitude(driverLatLng.longitude);

                    // Location class has a function which gives location between distances
                    float distanceBetweenLocation = loc1.distanceTo(loc2);

                    // to notify via button when the driver arrives
                    if (distanceBetweenLocation < 100){
                        mRequest.setText("Driver's Here");
                    }else {
                        mRequest.setText("Driver Found..." +
                                String.valueOf(distanceBetweenLocation));
                    }

                    // adding our marker to the map
                    mDriverMarker = mMap.addMarker(new MarkerOptions()
                            .position(driverLatLng).title("Your Driver")
                            .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_driver)));
                }
            }

            // we wont be using this function
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    // function to show drivers info
    private void getDriverInfo(){
        // we need to make the layout visible when we accept request
        mDriverInfo.setVisibility(View.VISIBLE);

        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference()
                .child("Users")
                .child("Riders")
                .child(driverFoundId);
        mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if (map.get("name")!=null){
                        mDriverName.setText(map.get("name").toString());
                    }
                    if (map.get("phone")!=null){
                        mDriverPhone.setText(map.get("phone").toString());
                    }
                    if (map.get("car")!=null){
                        mDriverCar.setText(map.get("car").toString());
                    }
                    if (map.get("profileImageUrl")!=null){
                        // using Glide we move the image using url into the ImageView
                        Glide.with(getApplicationContext())
                                .load(map.get("profileImageUrl").toString())
                                .into(mDriverImage);
                    }
                }
            }

            // we wont use this for now
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    private DatabaseReference driveHasEndedRef;
    private ValueEventListener driveHasEndedRefListener;

    private void getHasRideEnded(){
        driveHasEndedRef = FirebaseDatabase.getInstance().getReference()
                .child("Users")
                .child("Riders")
                .child(driverFoundId)
                .child("customerRequest")
                .child("customerRideId");

        driveHasEndedRefListener = driveHasEndedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // if exists then the rie is still continuing so we don't do anything
                // so we will work on the else part when the ride stops
                if (dataSnapshot.exists()){

                }
                // we only want to remove from DB when ride ends or cancels
                else {
                    endRide();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    private void endRide(){
        requestBol = false;
        geoQuery.removeAllListeners();
        driverLocationRef.removeEventListener(driverLocationRefListener);
        driveHasEndedRef.removeEventListener(driveHasEndedRefListener);

        // to remove from DB
        // to remove from the child of driver table which has a customer id
        if (driverFoundId != null){
            DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference()
                    .child("Users")
                    .child("Riders")
                    .child(driverFoundId)
                    .child("");
            driverRef.removeValue();
            driverFoundId = null;

        }
        driverFound = false;
        radius = 1;

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().
                getReference("customerRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);

        // we remove the marker
        if (pickupMarker != null){
            pickupMarker.remove();
        }

        if (mDriverMarker != null ){
            mDriverMarker.remove();
        }

        // we set all the details to null
        mDriverInfo.setVisibility(View.GONE);
        mDriverName.setText("");
        mDriverPhone.setText("");
        mDriverImage.setImageResource(R.mipmap.user);

        mRequest.setText("Call Cab");
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CustomerMapActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
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
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CustomerMapActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
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

    final int LOCATION_REQUEST_CODE = 1;
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){
            case LOCATION_REQUEST_CODE:{
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    mapFragment.getMapAsync(this);
                }
                else {
                    Toast.makeText(getApplicationContext(),
                            "Please Provide the Location or GPS permission",
                            Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    // to check when the Customer is not logged in or not available
    @Override
    protected void onStop() {
        super.onStop();
    }
}

