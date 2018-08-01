package eu.wonderfulme.triptracker.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.common.util.CollectionUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.wonderfulme.triptracker.App;
import eu.wonderfulme.triptracker.R;
import eu.wonderfulme.triptracker.database.LocationDbSingleton;
import eu.wonderfulme.triptracker.database.LocationHeaderData;
import eu.wonderfulme.triptracker.location.SearchLocation;
import eu.wonderfulme.triptracker.utility.UtilsSharedPref;

import static eu.wonderfulme.triptracker.location.LocationService.ACTION_PARKING_LOCATION_SAVED;
import static eu.wonderfulme.triptracker.location.SearchLocation.LOCATION_TYPE_SINGLE;
import static eu.wonderfulme.triptracker.location.SearchLocation.LOCATION_TYPE_TRACK;
import static eu.wonderfulme.triptracker.ui.DetailActivity.ACTION_ROUTE_REMOVED;

public class MainActivity extends AppCompatActivity implements RoutesRecyclerViewAdapter.ItemClickListener {

    private static final int MY_PERMISSIONS_REQUEST_FINE_LOCATION_MAIN_SINGLE = 101;
    private static final int MY_PERMISSIONS_REQUEST_FINE_LOCATION_MAIN_TRACK = 102;
    private final String KEY_RECYCLER_VIEW_SAVE_STATE = "KEY_RECYCLER_VIEW_SAVE_STATE";
    private final String KEY_IS_EVERYTHING_DISABLED_SAVE_STATE = "KEY_IS_EVERYTHING_DISABLED_SAVE_STATE";
    private final String KEY_TRACKING_SERVICE_SAVE_STATE = "KEY_TRACKING_SERVICE_SAVE_STATE";
    static final int ITEM_REMOVED_REQUEST = 103;
    static final String INTENT_EXTRA_ITEM_KEY = "INTENT_EXTRA_ITEM_KEY";
    static final String INTENT_EXTRA_ROUTE_NAME = "INTENT_EXTRA_ROUTE_NAME";

    @BindView(R.id.btn_main_save_parking) protected Button mSaveParkingButton;
    @BindView(R.id.btn_main_remove_parking) protected Button mRemoveParkingButton;
    @BindView(R.id.btn_main_record) protected Button mRecordButton;
    @BindView(R.id.recyclerView_routes) protected RecyclerView mRecyclerView;
    private RoutesRecyclerViewAdapter mAdapter;

    @BindView(R.id.constraintLayout_mainActivity) protected ConstraintLayout mConstraintLayout;
    @BindView(R.id.progressBar_main) protected ProgressBar mProgressBar;
    private BroadcastReceiver mLocationServiceReceiver;
    private boolean mIsEverythingDisabled = false;
    private SearchLocation mTrackingService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        // Check the location if it is valid show the restore button.
        List<String> parkingLocation = UtilsSharedPref.getParkingLocationFromSharedPref(this);
        if (!CollectionUtils.isEmpty(parkingLocation)) {
            mSaveParkingButton.setText(R.string.btn_restore_parking);
            mRemoveParkingButton.setEnabled(true);
        } else {
            mRemoveParkingButton.setEnabled(false);
        }

        mLocationServiceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (StringUtils.equals(action, ACTION_PARKING_LOCATION_SAVED)) {
                    showProgressBar(false);
                    mSaveParkingButton.setText(R.string.btn_restore_parking);
                    mRemoveParkingButton.setEnabled(true);
                    Snackbar.make(mConstraintLayout, getString(R.string.toast_parking_saved), Snackbar.LENGTH_LONG).show();
                }
            }
        };

        // Init recyclerView.
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new RoutesRecyclerViewAdapter(this, new ArrayList<LocationHeaderData>());
        mAdapter.setItemClickListener(this);
        mRecyclerView.setAdapter(mAdapter);

        new RecyclerViewUpdateAsyncTask().execute();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_RECYCLER_VIEW_SAVE_STATE, mRecyclerView.getLayoutManager().onSaveInstanceState());
        outState.putBoolean(KEY_IS_EVERYTHING_DISABLED_SAVE_STATE, mIsEverythingDisabled);
        outState.putParcelable(KEY_TRACKING_SERVICE_SAVE_STATE, mTrackingService);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mRecyclerView.getLayoutManager().onRestoreInstanceState(savedInstanceState.getParcelable(KEY_RECYCLER_VIEW_SAVE_STATE));
        mTrackingService = savedInstanceState.getParcelable(KEY_TRACKING_SERVICE_SAVE_STATE);
        mIsEverythingDisabled = savedInstanceState.getBoolean(KEY_IS_EVERYTHING_DISABLED_SAVE_STATE);
        if (mIsEverythingDisabled) {
            disableEverything();
            mRecordButton.setText(R.string.btn_main_record_stop);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(ACTION_PARKING_LOCATION_SAVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocationServiceReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocationServiceReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_setting) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_about) {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem settingsItem = menu.findItem(R.id.action_setting);
        MenuItem aboutItem = menu.findItem(R.id.action_about);
        if (mIsEverythingDisabled) {
            settingsItem.setEnabled(false);
            aboutItem.setEnabled(false);
        } else {
            settingsItem.setEnabled(true);
            aboutItem.setEnabled(true);
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        showProgressBar(false);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // permission was granted.
            switch (requestCode) {
                case MY_PERMISSIONS_REQUEST_FINE_LOCATION_MAIN_SINGLE:
                    checkGPSAndStartParkingService();
                    break;
                case MY_PERMISSIONS_REQUEST_FINE_LOCATION_MAIN_TRACK:
                    disableEverything();
                    mRecordButton.setText(R.string.btn_main_record_stop);
                    checkGPSAndStartTrackingService();
                    Snackbar.make(mConstraintLayout, getString(R.string.snackbar_put_app_in_background), Snackbar.LENGTH_LONG).show();
                    break;
                default:
                    break;
            }
        } else {
            // permission denied. Disable the functionality.
            Snackbar.make(mConstraintLayout, getString(R.string.toast_location_permission_denied), Snackbar.LENGTH_LONG).show();
        }
    }

    public void onSaveParkingClicked(View v) {
        if (mSaveParkingButton.getText().toString().equals(getString(R.string.btn_save_parking))) {
            showProgressBar(true);
            saveParkingLocation();
        } else {
            openParkingLocation();
        }
    }

    public void onRemoveParkingClicked(View v) {
        UtilsSharedPref.setParkingLocationToSharedPref(this, null);
        mSaveParkingButton.setText(getString(R.string.btn_save_parking));
        mRemoveParkingButton.setEnabled(false);
        Snackbar.make(mConstraintLayout, getString(R.string.toast_parking_removed), Snackbar.LENGTH_SHORT).show();
    }

    public void onRecordClicked(View v) {
        // if record button is clicked.
        if (mRecordButton.getText().toString().equals(getString(R.string.btn_main_record_start))) {
            // Check permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted. ask user.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_FINE_LOCATION_MAIN_TRACK);
            } else {
                //Permission is already granted.
                disableEverything();
                mRecordButton.setText(R.string.btn_main_record_stop);
                checkGPSAndStartTrackingService();
                Snackbar.make(mConstraintLayout, getString(R.string.snackbar_put_app_in_background), Snackbar.LENGTH_LONG).show();
            }
        } else { // if stop record is clicked.
            mTrackingService.stopService();
            mRecordButton.setText(getString(R.string.btn_main_record_start));
            enableEverything();
            new RecyclerViewUpdateAsyncTask().execute();
            Snackbar.make(mConstraintLayout, getString(R.string.snackbar_record_finished), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void showProgressBar(boolean show) {
        mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        mRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void saveParkingLocation() {
        // Check permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted. ask user.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_FINE_LOCATION_MAIN_SINGLE);
        } else {
            //Permission is already granted.
            checkGPSAndStartParkingService();
        }
    }

    private void openParkingLocation() {
        List<String> parkingLocationList = UtilsSharedPref.getParkingLocationFromSharedPref(this);
        if (parkingLocationList != null) {
            String latitude = parkingLocationList.get(0);
            String longitude = parkingLocationList.get(1);
            String uri = "https://www.google.com/maps/dir/?api=1&origin=Your+location&destination=" + latitude + "," + longitude;
            Uri mapUri = Uri.parse(uri);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, mapUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                mapIntent = new Intent(Intent.ACTION_VIEW, mapUri);
                startActivity(mapIntent);
            }
            // Remove location from shared pref.
            UtilsSharedPref.setParkingLocationToSharedPref(this, null);
            // Set button to parking save parking again.
            mSaveParkingButton.setText(R.string.btn_save_parking);
            mRemoveParkingButton.setEnabled(false);
        }
    }

    private void checkGPSAndStartParkingService() {
        SearchLocation searchLocation = new SearchLocation(this, LOCATION_TYPE_SINGLE);
        boolean isGpsOn = searchLocation.isGpsOn();
        if (App.getGoogleApiHelper().isConnected() && isGpsOn){
            searchLocation.startService();
        } else {
            showProgressBar(false);
            buildAlertMessageNoGps();
        }
    }

    private void checkGPSAndStartTrackingService() {
        if (mTrackingService == null) {
            mTrackingService = new SearchLocation(this, LOCATION_TYPE_TRACK);
        }
        boolean isGpsOn = mTrackingService.isGpsOn();
        if (App.getGoogleApiHelper().isConnected() && isGpsOn){
            mTrackingService.startService();
        } else {
            showProgressBar(false);
            buildAlertMessageNoGps();
        }
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.gps_is_not_on))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        // send user to location service setting.
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                        Toast.makeText(MainActivity.this, getString(R.string.toast_gps_denied), Toast.LENGTH_LONG).show();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    private void enableEverything() {
        for (int i = 0; i < mConstraintLayout.getChildCount(); ++i) {
            View v = mConstraintLayout.getChildAt(i);
            if (v.getId() != R.id.btn_main_record) {
                v.setEnabled(true);
            }
        }
        // Check remove and show parking buttons to enable them accordingly.
        List<String> parkingLocation = UtilsSharedPref.getParkingLocationFromSharedPref(this);
        mSaveParkingButton.setEnabled(parkingLocation == null);
        mRemoveParkingButton.setEnabled(parkingLocation != null);
        mAdapter.setClickable(true);

        mIsEverythingDisabled = false;
    }

    private void disableEverything() {
        for (int i = 0; i < mConstraintLayout.getChildCount(); ++i) {
            View v = mConstraintLayout.getChildAt(i);
            if (v.getId() != R.id.btn_main_record) {
                v.setEnabled(false);
            }
        }
        mAdapter.setClickable(false);
        mIsEverythingDisabled = true;
    }

    @Override
    public void onItemClick(LocationHeaderData headerData) {
        Intent routeItemIntent = new Intent(this, DetailActivity.class);
        routeItemIntent.putExtra(INTENT_EXTRA_ITEM_KEY, headerData.getItem_key());
        routeItemIntent.putExtra(INTENT_EXTRA_ROUTE_NAME, headerData.getMinTimestamp());
        startActivityForResult(routeItemIntent, ITEM_REMOVED_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ITEM_REMOVED_REQUEST: {
                if (resultCode == RESULT_OK) {
                    if (StringUtils.equals(data.getAction(), ACTION_ROUTE_REMOVED)) {
                        //Update RecyclerView
                        new RecyclerViewUpdateAsyncTask().execute();
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class RecyclerViewUpdateAsyncTask extends AsyncTask<Void, Void, List<LocationHeaderData>> {
        @Override
        protected List<LocationHeaderData> doInBackground(Void... voids) {
            return LocationDbSingleton.getInstance(MainActivity.this).locationDao().getLocationHeaderData();
        }

        @Override
        protected void onPostExecute(List<LocationHeaderData> locationHeaderData) {
            super.onPostExecute(locationHeaderData);
            mAdapter.swapData(locationHeaderData);
        }
    }
}
