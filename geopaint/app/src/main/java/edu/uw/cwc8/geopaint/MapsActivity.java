package edu.uw.cwc8.geopaint;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.menu.ActionMenuItemView;
import android.support.v7.widget.ShareActionProvider;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.xdty.preference.colorpicker.ColorPickerDialog;
import org.xdty.preference.colorpicker.ColorPickerSwatch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final String TAG = "Map Activity";

    private GoogleMap mMap;
    private UiSettings mUiSettings;
    GoogleApiClient mGoogleApiClient;
    private ShareActionProvider mShareActionProvider;

    private Polyline polyline;
    List<Polyline> lineList;
    private Marker currentLocation;

    private boolean penDown;
    private int mSelectedColor;

    private static final int LOC_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        getSupportActionBar();
        penDown = false;
        lineList = new ArrayList<Polyline>();
        mSelectedColor = ContextCompat.getColor(this, R.color.flamingo);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Create an instance of GoogleAPIClient.
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.pen:
                Log.v(TAG, "Select Pen");
                changePenStatus(item);
                return true;
            case R.id.picker:
                Log.v(TAG, "Select Picker");
                showColorPicker();
                return true;
            case R.id.save:
                Log.v(TAG, "Select Save");
                saveDrawing();
                return true;
            case R.id.share:
                Log.v(TAG, "Select Share");
                //instantiate the ShareActionProvider for share
                mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);
                handleShareFile(null);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //change the status of the pen (lifted or down)
    public void changePenStatus(MenuItem item){
        if(penDown == false) {
            item.setIcon(R.drawable.ic_draw_end);
            Toast.makeText(this,
                    "Start Drawing Mode",
                    Toast.LENGTH_SHORT).show();
            penDown = true;
            polyline = mMap.addPolyline(new PolylineOptions());
        } else if(penDown == true) {
            item.setIcon(R.drawable.ic_draw_start);
            Toast.makeText(this,
                    "End Drawing Mode",
                    Toast.LENGTH_SHORT).show();
            penDown = false;
        }
    }

    //show color picker
    // library from: https://github.com/xdtianyu/ColorPicker
    public void showColorPicker(){
        int[] mColors = getResources().getIntArray(R.array.default_rainbow);

        ColorPickerDialog dialog = ColorPickerDialog.newInstance(R.string.color_picker_default_title,
                mColors,
                mSelectedColor,
                5, // Number of columns
                ColorPickerDialog.SIZE_SMALL);

        dialog.setOnColorSelectedListener(new ColorPickerSwatch.OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color) {
                mSelectedColor = color;
                Log.v(TAG, "The selected color is: " + color);
            }
        });
        dialog.show(getFragmentManager(), "color_dialog_test");
    }

    //save the drawing on the map into a .geojson file
    public void saveDrawing(){
        Log.v(TAG, "Saving the drawing");

        if (penDown == true) {
            lineList.add(polyline);
        }

        //if we can write to the external storage
        if(isExternalStorageWritable()){
            try {
                File file = new File(this.getExternalFilesDir(null), "drawing.geojson");
                FileOutputStream outputStream = new FileOutputStream(file);
                GeoJsonConverter geoJsonConverter = new GeoJsonConverter();
                String geoJsonString = geoJsonConverter.convertToGeoJson(lineList);
                outputStream.write(geoJsonString.getBytes());

                Log.v(TAG, geoJsonString);

                //always make sure you close the stream so no data is leaked
                outputStream.close();
                Log.v(TAG, "Drawing Saved");
                Toast.makeText(this,
                        "Drawing Saved",
                        Toast.LENGTH_SHORT).show();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    //handles the sharing of the file using the ShareActionProvider
    public void handleShareFile(View v){
        Log.v(TAG, "Share button clicked");


        Uri fileUri;

        File dir = this.getExternalFilesDir(null);
        File file = new File(dir, "drawing.geojson");

        fileUri = Uri.fromFile(file);
        Log.v(TAG, "File is at: " + fileUri);

        //we can share a file by using intent with Extra
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain"); //set the type of intent
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);

        Toast.makeText(this,
                "Sharing File",
                Toast.LENGTH_SHORT).show();

        mShareActionProvider.setShareIntent(shareIntent);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mUiSettings = mMap.getUiSettings();

        //enable zoom in
        mUiSettings.setZoomControlsEnabled(true);
    }

    @Override
    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    /** Helper method for getting location **/
    public LatLng getLocation(View v) {
        Log.v(TAG, "Getting user location");
        LatLng latLng = null;

        if (mGoogleApiClient != null) {
            try {
                Location loc = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                if (loc != null) {
                    latLng = new LatLng(loc.getLatitude(), loc.getLongitude());

                    //display the user location on the screen
                    ((TextView) findViewById(R.id.txtLat)).setText("" + loc.getLatitude());
                    ((TextView) findViewById(R.id.txtLng)).setText("" + loc.getLongitude());

                    //remove the current marker and add a new one
                    if (currentLocation != null) {
                        currentLocation.remove();
                    }
                    currentLocation = mMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title("Your Current Location"));

                    Drawable icon = ContextCompat.getDrawable(this, R.drawable.ic_location_marker);
                    currentLocation.setIcon(BitmapDescriptorFactory.fromBitmap(((BitmapDrawable) icon).getBitmap()));
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                } else {
                    Log.v(TAG, "Cannot retrieve last location");
                    Toast.makeText(this,
                            "Cannot retrieve last location",
                            Toast.LENGTH_SHORT).show();
                }
            } catch (SecurityException e) {
                Log.v(TAG, "Permission to get location is required");
                e.printStackTrace();
            }
        }
        return latLng;
    }

    @Override
    public void onConnected(Bundle bundle) {
        //when API has connected
        LocationRequest request = new LocationRequest();
        request.setInterval(10000);
        request.setFastestInterval(5000);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if(permissionCheck == PackageManager.PERMISSION_GRANTED) {
            //if we have the permission, do the thing
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, request, this);
            getLocation(null);
        } else {
            //if the user denies the permission once, you should explain the rationale for the permission next time
            // see documentation for shouldShowRequestPermissionRationale
            //if(ActivityCompat.shouldShowRequestPermissionRationale(...))

            //if not, ask for the permission
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOC_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case LOC_REQUEST_CODE: { //if asked for location
                //see if we got any permission, and see what permission is granted
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onConnected(null); //then we run the onConnect method
                }
            }
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.v(TAG, "Connection Suspended");
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.v(TAG, "Location Change");
        if (penDown) {
            //get current location and draw line
            LatLng currentLocation = getLocation(null);
            List<LatLng> points = polyline.getPoints();
            points.add(currentLocation);
            polyline.setPoints(points);
            polyline.setColor(mSelectedColor);

            //add the line to the list
            lineList.add(polyline);
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.v(TAG, "Connection Failed");
    }
}