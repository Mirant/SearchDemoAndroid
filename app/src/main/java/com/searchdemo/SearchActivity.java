package com.searchdemo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.searchdemo.adapter.PlaceAutocompleteAdapter;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class SearchActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    public static final String TAG = "SearchActivity";

    private TextView facebookButton;
    private TextView userName;
    private TextView placeDetails;

    private CallbackManager callbackManager;

    private PlaceAutocompleteAdapter mAdapter;

    private AutoCompleteTextView mAutocompleteView;

    private LatLngBounds bounds = new LatLngBounds(
            new LatLng(-90, -180), new LatLng(90, 180));

    /**
     * Provides the entry point to Google Play services.
     */
    protected GoogleApiClient mGoogleApiClient;

    private InputMethodManager inputMethodManager;

    private RelativeLayout progressContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        //Initialize Facebook SDk
        FacebookSdk.sdkInitialize(getApplicationContext());

        initializeFacebookButton();

        userName = (TextView) findViewById(R.id.UserName);
        placeDetails = (TextView) findViewById(R.id.PlaceDetails);

        // Kick off the process of building a GoogleApiClient and requesting the LocationServices
        // API.
        buildGoogleApiClient();

        // Retrieve the AutoCompleteTextView that will display Place suggestions.
        mAutocompleteView = (AutoCompleteTextView) findViewById(R.id.autocompletePlaces);

        // Register a listener that receives callbacks when a suggestion has been selected
        mAutocompleteView.setOnItemClickListener(mAutocompleteClickListener);

        //Create places autocomplete adapter
        createPlaceAutocompleteAdapter();

        initializeProgress();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onConnected(Bundle bundle) {
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    /**
     * Show loading mask
     */
    private void initializeProgress()
    {
        progressContainer = (RelativeLayout) findViewById(R.id.ProgressContainer);
        progressContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });
    }

    /**
     * Initialize facebook button event
     */
    private void initializeFacebookButton()
    {
        facebookButton = (TextView)findViewById(R.id.FacebookConnectButton);
        facebookButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //If it is connected then show logout button otherwise show login button
                if("disconnected".contentEquals(view.getTag().toString()))
                {
                    progressContainer.setVisibility(View.VISIBLE);
                    onLoginButtonClicked();
                }
                else {
                    LoginManager.getInstance().logOut();
                    facebookButton.setTag("disconnected");
                    facebookButton.setText("Connect with Facebook");
                    userName.setText("Hello, Guest");
                }
            }
        });

        if(AccessToken.getCurrentAccessToken() != null)
        {
            facebookButton.setTag("connected");
            facebookButton.setText("Logout");
            showUserDetails(AccessToken.getCurrentAccessToken());
        }

        callbackManager = CallbackManager.Factory.create();
    }

    /**
     * On facebook connect button clicked
     */
    private void onLoginButtonClicked()
    {
        // Set permissions
        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("email,user_friends"));

        LoginManager.getInstance().registerCallback(callbackManager,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResult) {
                        facebookButton.setTag("connected");
                        facebookButton.setText("Logout");
                        showUserDetails(loginResult.getAccessToken());
                    }

                    @Override
                    public void onCancel() {
                        Log.i("facebook login", "cancel");
                        LoginManager.getInstance().logOut();
                        progressContainer.setVisibility(View.INVISIBLE);
                    }

                    @Override
                    public void onError(FacebookException error) {
                        Log.i("facebook login", error.getMessage());
                        progressContainer.setVisibility(View.INVISIBLE);
                    }
                });
    }

    /**
     * Get user's details from facebook access token
     * @param accessToken
     */
    private void showUserDetails(AccessToken accessToken)
    {
        GraphRequest request = GraphRequest.newMeRequest(
                accessToken,
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(
                            JSONObject object,
                            GraphResponse response) {
                        // Application code
                        progressContainer.setVisibility(View.INVISIBLE);
                        try {
                            userName.setText("Hello, " + object.getString("first_name"));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
        Bundle parameters = new Bundle();
        parameters.putString("fields", "id,name,email,first_name,last_name,picture");
        request.setParameters(parameters);
        request.executeAsync();
    }

    /**
     * Builds a GoogleApiClient. Uses the {@code #addApi} method to request the
     * LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        Log.i(TAG, "Building GoogleApiClient");
        mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .build();
    }

    /**
     * Create place autocomplete adapter
     */
    private void createPlaceAutocompleteAdapter() {
        // Set up the adapter that will retrieve suggestions from the Places Geo Data API that cover
        // the entire world.
        mAdapter = new PlaceAutocompleteAdapter(SearchActivity.this, R.layout.place_autocomplete_item_layout,
                mGoogleApiClient, bounds, null);
        mAutocompleteView.setAdapter(mAdapter);
    }

    /**
     * To hide keyboard
     */
    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * On place autocomplete list item click
     */
    private AdapterView.OnItemClickListener mAutocompleteClickListener
            = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            hideKeyboard();

            /*
             Retrieve the place ID of the selected item from the Adapter.
             The adapter stores each Place suggestion in a PlaceAutocomplete object from which we
             read the place ID.
              */
            final PlaceAutocompleteAdapter.PlaceAutocomplete item = mAdapter.getItem(position);
            final String placeId = String.valueOf(item.placeId);
            Log.i(TAG, "Autocomplete item selected: " + item.firstLine);

            /*
             Issue a request to the Places Geo Data API to retrieve a Place object with additional
              details about the place.
              */
            PendingResult<PlaceBuffer> placeResult = Places.GeoDataApi
                    .getPlaceById(mGoogleApiClient, placeId);
            placeResult.setResultCallback(mUpdatePlaceDetailsCallback);

            Log.i(TAG, "Called getPlaceById to get Place details for " + item.placeId);
        }
    };

    /**
     * Callback for results from a Places Geo Data API query that shows the first place result in
     * the details view on screen.
     */
    private ResultCallback<PlaceBuffer> mUpdatePlaceDetailsCallback
            = new ResultCallback<PlaceBuffer>() {
        @Override
        public void onResult(PlaceBuffer places) {
            if (!places.getStatus().isSuccess()) {
                // Request did not complete successfully
                Log.e(TAG, "Place query did not complete. Error: " + places.getStatus().toString());
                places.release();
                return;
            }
            // Get the Place object from the buffer.
            final Place place = places.get(0);

            //If place found, show its lat lng
            if (place != null) {
                LatLng latLng = place.getLatLng();
                placeDetails.setText("Latitude: " + latLng.latitude + ", Longitude: " + latLng.longitude);
            }
            Log.i(TAG, "Place details received: " + place.getName());

            places.release();
        }
    };
}
