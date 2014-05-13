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

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;
import com.google.protobuf.ByteString;
import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPProtocol.Response;
import org.mitre.svmp.protocol.SVMPProtocol.Response.ResponseType;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * S->C: Receives intercepted Notification broadcasts, converts them to Protobuf messages, and sends them to the client
 * @author Colin Courtney, Joe Portner
 */
public class NotificationHandler extends BaseHandler {
    private static final String TAG = NotificationHandler.class.getName();

    public NotificationHandler(BaseServer baseServer) {
        super(baseServer, INTERCEPT_NOTIFICATION_ACTION);
    }

    public void onReceive(Context context, Intent intent) {
        // validate the action of the broadcast (INTERCEPT_NOTIFICATION_ACTION is NOT a protected broadcast though)
        if (intent.getAction().equals(INTERCEPT_NOTIFICATION_ACTION)) {
            // pull the intercepted notification
            Notification notification = intent.getParcelableExtra("notification");

            // attempt to build the Protobuf message
            Response response = buildNotificationResponse(notification);

            // if we didn't encounter an error, send the Protobuf message
            if( response != null )
                sendMessage(response);
        }
    }

    // we don't need to receive any messages from the client, we only send them to the client

    //Pull out whatever info we can from the Notification.
    @SuppressWarnings("unchecked")
    private Response buildNotificationResponse(Notification notification) {
        RemoteViews view = notification.tickerView;
        if (view == null) {
            Log.d(TAG, "Failed to convert intercepted notification into a Protobuf message, tickerView is null");
            return null;
        }

        try {
            Class<? extends RemoteViews> secretClass = view.getClass();
            Map<Integer, String> text = new HashMap<Integer, String>();

            Field outerFields[] = secretClass.getDeclaredFields();
            for (int i = 0; i < outerFields.length; i++) {
                if (!outerFields[i].getName().equals("mActions")) continue;

                outerFields[i].setAccessible(true);

                ArrayList<Object> actions = (ArrayList<Object>) outerFields[i]
                        .get(view);
                for (Object action : actions) {
                    Field innerFields[] = action.getClass().getDeclaredFields();

                    Object value = null;
                    Integer type = null;
                    Integer viewId = null;
                    for (Field field : innerFields) {
                        field.setAccessible(true);
                        if (field.getName().equals("value")) {
                            value = field.get(action);
                        } else if (field.getName().equals("type")) {
                            type = field.getInt(action);
                        } else if (field.getName().equals("viewId")) {
                            viewId = field.getInt(action);
                        }
                    }

                    if (type == 9 || type == 10) {
                        text.put(viewId, value.toString());
                    }
                }
                String title_details = text.get(16908310),
                        info_details = text.get(16909082),
                        inner_text_details = text.get(16908358);

                //Inflate ticker layout from pre-defined xml into a view.
                LayoutInflater inflater = (LayoutInflater) baseServer.getContext().getSystemService
                        (Context.LAYOUT_INFLATER_SERVICE);
                View root = inflater.inflate(R.layout.status_bar_latest_event_ticker, null);

                //Set the view's various items from the notification data.
                TextView title = (TextView) root.findViewById(R.id.title),
                        info = (TextView) root.findViewById(R.id.info),
                        inner_text = (TextView) root.findViewById(R.id.text);
                title.setText(title_details);
                info.setText(info_details);
                inner_text.setText(inner_text_details);

                //Get image from system resources.
                Context foreignContext = baseServer.getContext().createPackageContext(notification.tickerView.getPackage(),
                        Context.CONTEXT_IGNORE_SECURITY);
                byte[] byteArray = Utility.drawableToBytes(foreignContext.getResources().getDrawable(notification.icon));

                //Set the re-compiled notification ticker layout view as a toast, and display.
                Toast toast = new Toast(baseServer.getContext());
                toast.setView(root);
                toast.show();

                //Build the response and return it.
                return Response.newBuilder()
                        .setType(ResponseType.NOTIFICATION)
                        .setNotification(
                                SVMPProtocol.Notification.newBuilder()
                                        .setContentTitle(title != null ? title.getText().toString() : "SVMP INTERCEPTED NOTIFICATION")
                                        .setContentText(inner_text != null ? inner_text.getText().toString() : "No text.")
                                        .setSmallIcon(ByteString.copyFrom(byteArray))
                                        .build()
                        ).build();
            }
        } catch (Exception e) {
            Log.d(TAG, "Error converting intercepted notification into a Protobuf message: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    //Keep this for later just in case:
                     /* Re-create a 'local' view group from the info contained in the remote view
				LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				ViewGroup localView = (ViewGroup) inflater.inflate(notification.tickerView.getLayoutId(), null);
				notification.tickerView.reapply(context, localView);
				
				//Pull out requisite views.
				TextView title = (TextView)localView.findViewById(R.id.title),
					text    = (TextView)localView.findViewById(R.id.text);
				ImageView icon = (ImageView)localView.findViewById(R.id.icon);*/
				
				/*//Get icon byte array from 'large icon' field in notification.
				byte[] byteArray = new byte[256];
				if(notification.largeIcon != null)
				{
					ByteArrayOutputStream stream = new ByteArrayOutputStream();
					notification.largeIcon.compress(Bitmap.CompressFormat.PNG, 100, stream);
					byteArray = stream.toByteArray();
				}*/
				/*//Get icon byte array from ImageView.
				byte[] byteArray = new byte[256];
				Bitmap bitmap = ((BitmapDrawable)icon.getDrawable()).getBitmap();
				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
				byteArray = stream.toByteArray();*/

}
