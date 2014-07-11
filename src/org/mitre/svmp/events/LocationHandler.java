/*
 Copyright 2013 The MITRE Corporation, All Rights Reserved.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this work except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.mitre.svmp.events;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import org.mitre.svmp.protocol.SVMPProtocol.*;

/**
 * @author Joe Portner
 * C->S: Receives Location provider info/status, enabled, and updates from the client, and sends them to the LocationManager
 * S->C: Receives intercepted Location requests, converts them to Protobuf messages, and sends them back to the client
 */
public class LocationHandler extends BaseHandler {
    private static final String TAG = LocationHandler.class.getName();

    private LocationManager locationManager;
    DatabaseHandler handler;

    public LocationHandler(BaseServer baseServer) {
        super(baseServer, LOCATION_SUBSCRIBE_ACTION, LOCATION_UNSUBSCRIBE_ACTION);

        this.locationManager = (LocationManager) baseServer.getContext().getSystemService(Context.LOCATION_SERVICE);
        this.handler = new DatabaseHandler(baseServer.getContext());
    }

    // receive messages from the LocationManager service, pass them back to the client
    // we can't require a sender permission from LocationManager, so the subscribe and unsubscribe actions are
    // protected system broadcasts
    public void onReceive(Context context, Intent intent) {
        // check the Intent Action to make sure it is valid (either "start" or "stop")
        boolean start;
        if( (start = intent.getAction().equals( LOCATION_SUBSCRIBE_ACTION) )
                || !(start = !intent.getAction().equals( LOCATION_UNSUBSCRIBE_ACTION) ) ) {
            Log.d(TAG, "Received location subscribe intent broadcast");

            // get extras from Intent
            String provider = intent.getStringExtra("provider");

            // if this broadcast is for a passive provider, return; the LocationManager shouldn't send us broadcasts
            // for passive providers, this is just a failsafe
            if( provider.equals(LocationManager.PASSIVE_PROVIDER) )
                return;

            long minTime = intent.getLongExtra("minTime", 0L);
            float minDistance = intent.getFloatExtra("minDistance", 0.0f);
            boolean singleShot = intent.getBooleanExtra("singleShot", false);

            // construct Subscription for this request
            LocationSubscription locationSubscription = new LocationSubscription(provider, minTime, minDistance);

            // get the foremost subscription (with the lowest combined minTime and minDistance)
            LocationSubscription foremostLocationSubscription = handler.getForemostSubscription(provider);

            // this Intent directs us to start a new subscription
            if( start ) {
                // if this is not a "SingleShot" subscription, add it to the sorted list of subscriptions
                if( !singleShot )
                    handler.insertSubscription(locationSubscription);

                // if the foremost subscription doesn't satisfy this one, send a message to make a new subscription
                if( foremostLocationSubscription == null || !foremostLocationSubscription.satisfies(locationSubscription) ) {
                    // this is a long-term subscription, get the lowest minTime and minDistance
                    if( !singleShot && foremostLocationSubscription != null ) {
                        if( foremostLocationSubscription.getMinTime() < minTime )
                            minTime = foremostLocationSubscription.getMinTime();
                        if( foremostLocationSubscription.getMinDistance() < minDistance )
                            minDistance = foremostLocationSubscription.getMinDistance();

                        // to avoid making another database call, we'll construct a new subscription here
                        locationSubscription = new LocationSubscription(provider, minTime, minDistance);
                    }

                    // send the message
                    sendMessage(Utility.buildSubscribeResponse(locationSubscription, singleShot));
                }
            }
            // this Intent directs us to stop an existing subscription
            else {
                long result = handler.deleteSubscription(locationSubscription);

                // if the deletion was successful, then we need to reload the foremost subscription to see if it has changed
                if( result > -1 ) {
                    // refresh the foremost subscription
                    foremostLocationSubscription = handler.getForemostSubscription(provider);

                    // if there is no foremost subscription for this provider, send a message to unsubscribe
                    if( foremostLocationSubscription == null )
                        sendMessage( Utility.buildUnsubscribeResponse(provider) );
                    // if we have detected a change in the foremost subscription, send a message to make a new subscription
                    // this saves battery life for the handset
                    else if( foremostLocationSubscription.getMinTime() > locationSubscription.getMinTime()
                            || foremostLocationSubscription.getMinDistance() > locationSubscription.getMinDistance() )
                        sendMessage(Utility.buildSubscribeResponse(foremostLocationSubscription, false));
                }
            }

            // cleanup
            handler.close();
        }
    }

    // receive messages from the client, pass them back to the LocationManager service
    protected void handleMessage(Request request) {
        if( request.hasLocationRequest() ) {
            LocationRequest locationRequest = request.getLocationRequest();

            switch( locationRequest.getType() ) {
                case PROVIDERINFO:
                    if( locationRequest.hasProviderInfo() )
                        handleProviderInfo(locationRequest.getProviderInfo());
                    else
                        Log.d(TAG, "LocationProviderInfo not found");
                    break;
                case PROVIDERSTATUS:
                    if( locationRequest.hasProviderStatus() )
                        handleProviderStatus(locationRequest.getProviderStatus());
                    else
                        Log.d(TAG, "LocationProviderStatus not found");
                    break;
                case PROVIDERENABLED:
                    if( locationRequest.hasProviderEnabled() )
                        handleProviderEnabled(locationRequest.getProviderEnabled());
                    else
                        Log.d(TAG, "LocationProviderEnabled not found");
                    break;
                case LOCATIONUPDATE:
                    if( locationRequest.hasUpdate() )
                        handleUpdate(locationRequest.getUpdate());
                    else
                        Log.d(TAG, "LocationUpdate not found");
                    break;
            }
        }
    }

    private void handleProviderInfo(LocationProviderInfo locationProviderInfo) {
        // get provider name
        String provider = locationProviderInfo.getProvider();

        if( validProvider(provider) ) {
            // try to add a new test provider (there's no way to check and see if one already exists)
            try {
                // spoof this test provider to "overwrite" the legitimate one with the same name
                locationManager.addTestProvider(
                        provider,
                        locationProviderInfo.getRequiresNetwork(),
                        locationProviderInfo.getRequiresSatellite(),
                        locationProviderInfo.getRequiresCell(),
                        locationProviderInfo.getHasMonetaryCost(),
                        locationProviderInfo.getSupportsAltitude(),
                        locationProviderInfo.getSupportsSpeed(),
                        locationProviderInfo.getSupportsBearing(),
                        locationProviderInfo.getPowerRequirement(),
                        locationProviderInfo.getAccuracy());
            } catch( IllegalArgumentException e ) {
                // there was an existing test provider
            }

            // get the foremost subscription (with the lowest combined minTime and minDistance)
            LocationSubscription foremostLocationSubscription = handler.getForemostSubscription(provider);

            // cleanup
            handler.close();

            // send the foremost subscription for this provider back to the client
            if( foremostLocationSubscription != null )
                sendMessage( Utility.buildSubscribeResponse(foremostLocationSubscription, false) );
        }
    }

    private void handleProviderStatus(LocationProviderStatus locationProviderStatus) {
        // get provider name
        String provider = locationProviderStatus.getProvider();

        if( validProvider(provider) ) {
            // construct a Bundle from the Protobuf message
            Bundle extras = Utility.getBundle(locationProviderStatus);

            try {
                // spoof the status to the test provider
                locationManager.setTestProviderStatus(
                        provider,
                        locationProviderStatus.getStatus(),
                        extras,
                        System.currentTimeMillis());
            } catch( IllegalArgumentException e ) {
                Log.e(TAG, "Error setting test provider status: " + e.getMessage());
            }
        }
    }

    private void handleProviderEnabled(LocationProviderEnabled locationProviderEnabled) {
        // get provider name
        String provider = locationProviderEnabled.getProvider();

        if( validProvider(provider) ) {
            try {
                // spoof the enabled/disabled to the test provider
                locationManager.setTestProviderEnabled(
                        provider,
                        locationProviderEnabled.getEnabled());
            } catch( IllegalArgumentException e ) {
                Log.e(TAG, "Error setting test provider enabled: " + e.getMessage());
            }
        }
    }

    private void handleUpdate(LocationUpdate locationUpdate) {
        // get provider name
        String provider = locationUpdate.getProvider();

        if( validProvider(provider) ) {
            // construct a Location from the Protobuf message
            Location location = Utility.getLocation(locationUpdate);

            try {
                // spoof the update to the test provider
                locationManager.setTestProviderLocation(provider, location);
            } catch( IllegalArgumentException e ) {
                Log.e(TAG, "Error setting test provider location: " + e.getMessage());
            }
        }
    }

    private boolean validProvider(String provider) {
        return provider != null
                && provider.length() > 0
                && !provider.equals(LocationManager.PASSIVE_PROVIDER);
    }
}
