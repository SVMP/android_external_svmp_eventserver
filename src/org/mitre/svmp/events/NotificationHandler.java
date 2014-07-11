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
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.RemoteViews;
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
 * @author Colin Courtney, Joe Portner
 * S->C: Receives intercepted Notification broadcasts, converts them to Protobuf messages, and sends them to the client
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

            if (notification != null) {
                // handle the notification and send the appropriate message to the client
                handleNotification(notification);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleNotification(Notification notification) {
        RemoteViews view = notification.tickerView;
        if (view != null) {
            try {
                // utilize Java Reflection to extract text elements
                Class<? extends RemoteViews> secretClass = view.getClass();
                Map<Integer, String> textMap = new HashMap<Integer, String>();

                Field outerField = secretClass.getDeclaredField("mActions");
                outerField.setAccessible(true);

                ArrayList<Object> actions = (ArrayList<Object>) outerField.get(view);
                for (Object action : actions) {
                    Field innerFields[] = action.getClass().getDeclaredFields();
                    Field innerFieldsSuper[] = action.getClass().getSuperclass().getDeclaredFields();

                    Object value = null;
                    Integer type = null;
                    Integer viewId = null;
                    for (Field field : innerFields) {
                        field.setAccessible(true);
                        if (field.getName().equals("value")) {
                            value = field.get(action);
                        } else if (field.getName().equals("type")) {
                            type = field.getInt(action);
                        }
                    }
                    for (Field field : innerFieldsSuper) {
                        field.setAccessible(true);
                        if (field.getName().equals("viewId")) {
                            viewId = field.getInt(action);
                        }
                    }

                    if (value != null && type != null && viewId != null && (type == 9 || type == 10)) {
                        textMap.put(viewId, value.toString());
                    }
                }

                // get the title and inner text
                String title = textMap.get(16908310),
                        //info = textMap.get(16909082),
                        text = textMap.get(16908358);

                // get small icon image from system resources
                Context foreignContext = baseServer.getContext().createPackageContext(
                        notification.tickerView.getPackage(), Context.CONTEXT_IGNORE_SECURITY);
                Drawable smallIconDrawable = foreignContext.getResources().getDrawable(notification.icon);
                byte[] smallIconBytes = Utility.drawableToBytes(smallIconDrawable);

                // if a large icon exists, take the bitmap and convert it to a byte array
                byte[] largeIconBytes = null;
                if (notification.largeIcon != null) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    notification.largeIcon.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    largeIconBytes = stream.toByteArray();
                }

                // create a Response protobuf and send it
                Response response = buildNotificationResponse(title, text, smallIconBytes, largeIconBytes);
                sendMessage(response);
            } catch (Exception e) {
                Log.e(TAG, "handleNotification failed: ", e);
            }
        }
        else {
            Log.e(TAG, "handleNotification failed, tickerView is null");
        }
    }

    // we don't need to receive any messages from the client, we only send them to the client

    private Response buildNotificationResponse(String title, String text, byte[] smallIconBytes, byte[] largeIconBytes) {
        SVMPProtocol.Notification.Builder nBuilder = SVMPProtocol.Notification.newBuilder();
        // required attributes
        nBuilder.setContentTitle(title);
        nBuilder.setContentText(text);
        nBuilder.setSmallIcon(ByteString.copyFrom(smallIconBytes));
        // optional attributes
        if (largeIconBytes != null)
            nBuilder.setLargeIcon(ByteString.copyFrom(largeIconBytes));

        // wrap the Notification protobuf in a Response protobuf and return it
        return Response.newBuilder()
                .setType(ResponseType.NOTIFICATION)
                .setNotification(nBuilder)
                .build();
    }
}
