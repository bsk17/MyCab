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
import android.support.v4.widget.ImageViewCompat;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// we are implementing some classes of google api and location to get some functions to update the
// location every second

// we will be using GeoFire api to save data in Firebase DB

// we are using Routing Listener from github for routing directions

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener,
        RoutingListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;

    SupportMapFragment mapFragment;

    private Button mlogout, mSettings;

    private Boolean isLoggingOut = false;

    // this will be th customerId of the request made
    private String customerId="";

    // these are the variables for our customer profile when the request is received
    private LinearLayout mCustomerInfo;

    private ImageView mCustomerProfileImage;

    // variables to show customer info
    private TextView mCustomerName, mCustomerPhone, mCustomerDestination;

    // variables for route
    private List<Polyline> polylines;
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);

        // initializing polyLines
        polylines = new ArrayList<>();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        // double checking for permission
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DriverMapActivity.this,
                    new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }
        else {
            mapFragment.getMapAsync(this);
        }

        mSettings = findViewById(R.id.settings);
        mlogout = findViewById(R.id.logout);
        mCustomerInfo = findViewById(R.id.customerInfo);
        mCustomerProfileImage = findViewById(R.id.customerProfileImage);
        mCustomerName = findViewById(R.id.customerName);
        mCustomerPhone = findViewById(R.id.customerPhone);
        mCustomerDestination = findViewById(R.id.customerDestination);

        // function to logout using FireBase
        mlogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // this will be used to call the disconnect driver function at the last
                isLoggingOut = true;
                disconnectDriver();

                FirebaseAuth.getInstance().signOut();
                // after sign out we go to MainActivity to elect driver or customer
                Intent intent = new Intent(DriverMapActivity.this,
                        MainActivity.class);
                startActivity(intent);
                finish();
                return;

            }
        });

        // function to manage the driver profile via settings button
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DriverMapActivity.this,
                        DriverSettingsActivity.class);
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
                .child("Users")
                .child("Riders")
                .child(driverId)
                .child("customerRequest")
                .child("customerRideId");

        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // this will get activated as soon as there is child created of Riders
                // dataSnapshot means an element of child
                if (dataSnapshot.exists()){
                    customerId = dataSnapshot.getValue().toString();
                    getAssignedCustomerPickupLocation();

                    // this function is to show the details of customer when request is accepted
                    getAssignedCustomerInfo();

                    getAssignedCustomerDestination();
                }
                // driver canceled request notice when driver is removed
                else {
                    // this function is called when we cancel the request
                    erasePolylines();
                    customerId = "";
                    if (pickupMarker != null){
                        pickupMarker.remove();
                    }
                    if (assignedCustomerPickupLocationRefListener != null) {
                        assignedCustomerPickupLocationRef
                                .removeEventListener(assignedCustomerPickupLocationRefListener);
                    }

                    // we set all the details to null
                    mCustomerInfo.setVisibility(View.GONE);
                    mCustomerName.setText("");
                    mCustomerPhone.setText("");
                    mCustomerProfileImage.setImageResource(R.mipmap.user);
                    mCustomerDestination.setText("Destination -- ");
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
                .child("CustomerRequest")
                .child(customerId)
                .child("l");

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
                    LatLng pickupLatLng = new LatLng(locationLat, locationLng);

                    // adding our marker to the map
                    pickupMarker = mMap.addMarker(new MarkerOptions()
                            .position(pickupLatLng).title("Pickup Location")
                            .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));

                    // creating route
                    getRouteToMarker(pickupLatLng);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void getRouteToMarker(LatLng pickupLatLng) {
        Routing routing = new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude())
                        , pickupLatLng)
                .build();
        routing.execute();
    }

    private void getAssignedCustomerDestination(){
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference()
                .child("Users")
                .child("Riders")
                .child(driverId)
                .child("customerRequest")
                .child("destination");

        assignedCustomerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()){
                    String destination = dataSnapshot.getValue().toString();
                    mCustomerDestination.setText("Destination -- " + destination);
                }
                else {
                    mCustomerDestination.setText("Destination -- ");
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }

            });
        }

    private void getAssignedCustomerInfo(){
        // we need to make the layout visible when we accept request
        mCustomerInfo.setVisibility(View.VISIBLE);

        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users")
                .child("Customers").child(customerId);
        mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if (map.get("name")!=null){
                        mCustomerName.setText(map.get("name").toString());
                    }
                    if (map.get("phone")!=null){
                        mCustomerPhone.setText(map.get("phone").toString());
                    }
                    if (map.get("profileImageUrl")!=null){
                        // using Glide we move the image using url into the ImageView
                        Glide.with(getApplicationContext())
                                .load(map.get("profileImageUrl").toString())
                                .into(mCustomerProfileImage);
                    }
                }
            }

            // we wont use this for now
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

        // we check for permission
        // once we call this function then onRequestPermissionResult is called
        // LOCATION_REQUEST_CODE is the code for the permission which will be used later below
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DriverMapActivity.this,
                    new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
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

    public void disconnectDriver(){
        // when the driver logs out then we remove the location from the database
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("DriversAvailable");

        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);
    }

    // handling permission intent result
    final int LOCATION_REQUEST_CODE = 1;
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions
            , @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){
            case LOCATION_REQUEST_CODE:{
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
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

    // this function will generally get called when the driver logs out
    @Override
    protected void onStop() {
        super.onStop();
        if (!isLoggingOut){
            disconnectDriver();
        }
    }

    // these are the implemented functions of RoutingListener Interface
    // copy and paste the code from github
    @Override
    public void onRoutingFailure(RouteException e) {
        if(e != null) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {
        if(polylines.size()>0) {
            for (Polyline poly : polylines) {
                poly.remove();
            }
        }

        polylines = new ArrayList<>();
        //add route(s) to the map.
        for (int i = 0; i <route.size(); i++) {

            //In case of more than 5 alternative routes
            int colorIndex = i % COLORS.length;

            PolylineOptions polyOptions = new PolylineOptions();
            polyOptions.color(getResources().getColor(COLORS[colorIndex]));
            polyOptions.width(10 + i * 3);
            polyOptions.addAll(route.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polyOptions);
            polylines.add(polyline);

            Toast.makeText(getApplicationContext(),
                    "Route "+ (i+1) +": distance - "+
                            route.get(i).getDistanceValue()+": duration - "+
                            route.get(i).getDurationValue()
                    ,Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingCancelled() {
    }

    // to clear the route from the map
    private void erasePolylines(){
        for (Polyline line : polylines){
            line.remove();
        }
        polylines.clear();
    }
}
