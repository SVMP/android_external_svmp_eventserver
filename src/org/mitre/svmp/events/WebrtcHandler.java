/*
 * Copyright (c) 2013 The MITRE Corporation, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this work except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Derived from AppRTCDemoActivity and AppRTCClient from the libjingle / webrtc AppRTCDemo
// example application distributed under the following license.
/*
 * libjingle
 * Copyright 2013, Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.mitre.svmp.events;

import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mitre.svmp.protocol.SVMPProtocol.Request;
import org.mitre.svmp.protocol.SVMPProtocol.Response;
import org.mitre.svmp.protocol.SVMPProtocol.Response.ResponseType;
import org.mitre.svmp.protocol.SVMPProtocol.VideoStreamInfo;
import org.mitre.svmp.protocol.SVMPProtocol.WebRTCMessage;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.StatsObserver;
import org.webrtc.StatsReport;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import android.os.PowerManager;
import android.util.Log;

public class WebrtcHandler {
    private static final String TAG = BaseServer.class.getName();
    
    private PeerConnection pc;
    private final PCObserver pcObserver = new PCObserver();
    private final SDPObserver sdpObserver = new SDPObserver();
    private MediaConstraints sdpMediaConstraints;
    
    private LinkedList<IceCandidate> queuedRemoteCandidates =
        new LinkedList<IceCandidate>();
    // Synchronize on quit[0] to avoid teardown-related crashes.
    private final Boolean[] quit = new Boolean[] { false };

    private final List<PeerConnection.IceServer> iceServers;
    private final boolean initiator = false;
    private final MediaConstraints pcConstraints;
    private final MediaConstraints videoConstraints;
    
    private BaseServer base;
    
    public WebrtcHandler(BaseServer baseServer, VideoStreamInfo vidInfo) {
        base = baseServer;
        
        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
            "OfferToReceiveAudio", "false"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
            "OfferToReceiveVideo", "true"));
        
        pcConstraints = constraintsFromJSON(vidInfo.getPcConstraints());
        Log.d(TAG, "pcConstraints: " + pcConstraints);

        videoConstraints = constraintsFromJSON(vidInfo.getVideoConstraints());
        Log.d(TAG, "videoConstraints: " + videoConstraints);

        iceServers = iceServersFromPCConfigJSON(vidInfo.getIceServers());
        onIceServers(iceServers);
    }
    
    public void handleMessage(Request msg) {
        try {
            JSONObject json = new JSONObject(msg.getWebrtcMsg().getJson());
            String type;
            try {
                type = (String) json.get("type");
            }
            catch( JSONException e) {
                json.put("type", "candidate");
                type = (String) json.get("type");
            }
            if (type.equals("candidate")) {
                IceCandidate candidate = new IceCandidate(
                        // (String) json.get("id"),
                        // json.getInt("label"),
                        (String) json.get("sdpMid"),
                        json.getInt("sdpMLineIndex"),
                        (String) json.get("candidate"));
                if (queuedRemoteCandidates != null) {
                    queuedRemoteCandidates.add(candidate);
                } else {
                    pc.addIceCandidate(candidate);
                }
            } else if (type.equals("answer") || type.equals("offer")) {
                SessionDescription sdp = new SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(type),
                        (String) json.get("sdp"));
                pc.setRemoteDescription(sdpObserver, sdp);
            } else if (type.equals("bye")) {
                Log.d(TAG, "Remote end hung up; dropping PeerConnection");
                disconnectAndExit();
            } else {
                throw new RuntimeException("Unexpected message: " + msg);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
   
//  @Override
//  public void onCreate(Bundle savedInstanceState) {
//    super.onCreate(savedInstanceState);
//
//    // Since the error-handling of this demo consists of throwing
//    // RuntimeExceptions and we assume that'll terminate the app, we install
//    // this default handler so it's applied to background threads as well.
//    Thread.setDefaultUncaughtExceptionHandler(
//        new Thread.UncaughtExceptionHandler() {
//          public void uncaughtException(Thread t, Throwable e) {
//            e.printStackTrace();
//            System.exit(-1);
//          }
//        });
//
//    // Uncomment to get ALL WebRTC tracing and SENSITIVE libjingle logging.
//    // Logging.enableTracing(
//    //     "/sdcard/trace.txt",
//    //     EnumSet.of(Logging.TraceLevel.TRACE_ALL),
//    //     Logging.Severity.LS_SENSITIVE);
//
//    abortUnless(PeerConnectionFactory.initializeAndroidGlobals(this),
//        "Failed to initializeAndroidGlobals");
//
//    AudioManager audioManager =
//        ((AudioManager) getSystemService(AUDIO_SERVICE));
//    audioManager.setMode(audioManager.isWiredHeadsetOn() ?
//        AudioManager.MODE_IN_CALL : AudioManager.MODE_IN_COMMUNICATION);
//    audioManager.setSpeakerphoneOn(!audioManager.isWiredHeadsetOn());
//
//    sdpMediaConstraints = new MediaConstraints();
//    sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
//        "OfferToReceiveAudio", "false"));
//    sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
//        "OfferToReceiveVideo", "true"));
//
//    // Get info passed to Intent
//    final Intent intent = getIntent();
//    host = intent.getExtras().getString("host");
//    port = intent.getExtras().getInt("port");
//    encryptionType = intent.getExtras().getInt("encryptionType");
//
//    connectToRoom();
//  }

    private void onIceServers(List<PeerConnection.IceServer> iceServers) {
        PeerConnectionFactory factory = new PeerConnectionFactory();

        pc = factory.createPeerConnection(iceServers, pcConstraints, pcObserver);

        {
            final PeerConnection finalPC = pc;
            final Runnable repeatedStatsLogger = new Runnable() {
                public void run() {
                    synchronized (quit[0]) {
                        if (quit[0]) {
                            return;
                        }
                        final Runnable runnableThis = this;
                        boolean success = finalPC.getStats(new StatsObserver() {
                            public void onComplete(StatsReport[] reports) {
                                for (StatsReport report : reports) {
                                    Log.d(TAG, "Stats: " + report.toString());
                                }
                                //vsv.postDelayed(runnableThis, 10000);
                            }
                        }, null);
                        if (!success) {
                            throw new RuntimeException(
                                    "getStats() return false!");
                        }
                    }
                }
            };
        }

        {
            Log.d(TAG, "Creating local video source...");
            VideoCapturer capturer = VideoCapturer.create();

            VideoSource videoSource = factory.createVideoSource(capturer, videoConstraints);
            MediaStream lMS = factory.createLocalMediaStream("ARDAMS");
            VideoTrack videoTrack = factory.createVideoTrack("ARDAMSv0", videoSource);
            // videoTrack.addRenderer(new VideoRenderer(new VideoCallbacks(
            //     vsv, VideoStreamsView.Endpoint.LOCAL)));
            lMS.addTrack(videoTrack);
            lMS.addTrack(factory.createAudioTrack("ARDAMSa0"));
            pc.addStream(lMS, new MediaConstraints());
        }

        Log.d(TAG, "Waiting for ICE candidates...");
    }

    // Cycle through likely device names for the camera and return the first
    // capturer that works, or crash if none do.
    /*private VideoCapturer getVideoCapturer() {
        String[] cameraFacing = { "front", "back" };
        int[] cameraIndex = { 0, 1 };
        int[] cameraOrientation = { 0, 90, 180, 270 };
        for (String facing : cameraFacing) {
            for (int index : cameraIndex) {
                for (int orientation : cameraOrientation) {
                    String name = "Camera " + index + ", Facing " + facing
                            + ", Orientation " + orientation;
                    VideoCapturer capturer = VideoCapturer.create(name);
                    if (capturer != null) {
                        Log.d(TAG, "Using camera: " + name);
                        return capturer;
                    }
                }
            }
        }
        throw new RuntimeException("Failed to open capturer");
    }*/

    // Send |json| to the underlying AppEngine Channel.
    private void sendMessage(JSONObject msg) {
        WebRTCMessage.Builder rtcmsg = WebRTCMessage.newBuilder();
        rtcmsg.setJson(msg.toString());

        sendMessage(Response.newBuilder().setType(ResponseType.WEBRTC)
                .setWebrtcMsg(rtcmsg).build());
    }
  
    public void sendMessage(Response msg) {
        base.sendMessage(msg);
    }

    // Put a |key|->|value| mapping in |json|.
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    // Implementation detail: observe ICE & stream changes and react accordingly.
    private class PCObserver implements PeerConnection.Observer {
        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            JSONObject json = new JSONObject();
            jsonPut(json, "type", "candidate");
            // jsonPut(json, "label", candidate.sdpMLineIndex);
            // jsonPut(json, "id", candidate.sdpMid);
            jsonPut(json, "sdpMLineIndex", candidate.sdpMLineIndex);
            jsonPut(json, "sdpMid", candidate.sdpMid);
            jsonPut(json, "candidate", candidate.sdp);
            sendMessage(json);
        }

        @Override
        public void onError() {
            throw new RuntimeException("PeerConnection error!");
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState newState) {
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
        }

        @Override
        public void onAddStream(final MediaStream stream) {
            // there shouldn't be any streams from the client until we
            // implement camera and microphone forwarding
            // abortUnless(//stream.audioTracks.size() == 1 &&
            // stream.videoTracks.size() == 1,
            // "Weird-looking stream: " + stream);
            // stream.videoTracks.get(0).addRenderer(new VideoRenderer(
            // new VideoCallbacks(vsv, VideoStreamsView.Endpoint.REMOTE)));
        }

        @Override
        public void onRemoveStream(final MediaStream stream) {
            stream.videoTracks.get(0).dispose();
        }

        @Override
        public void onDataChannel(final DataChannel dc) {
            // there shouldn't be any data channels from the client until
            // we implement P2P touch/sensor/etc. input
        }
    }

    // Implementation detail: handle offer creation/signaling and answer
    // setting, as well as adding remote ICE candidates once the answer 
    // SDP is set.
    private class SDPObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(final SessionDescription sdp) {
            Log.d(TAG, "Sending " + sdp.type);
            JSONObject json = new JSONObject();
            jsonPut(json, "type", sdp.type.canonicalForm());
            jsonPut(json, "sdp", sdp.description);
            sendMessage(json);
            pc.setLocalDescription(sdpObserver, sdp);
        }

        @Override
        public void onSetSuccess() {
            if (initiator) {
                if (pc.getRemoteDescription() != null) {
                    // We've set our local offer and received & set the remote
                    // answer, so drain candidates.
                    drainRemoteCandidates();
                }
            } else {
                if (pc.getLocalDescription() == null) {
                    // We just set the remote offer, time to create our answer.
                    Log.d(TAG, "Creating answer");
                    pc.createAnswer(SDPObserver.this, sdpMediaConstraints);
                } else {
                    // Sent our answer and set it as local description; drain
                    // candidates.
                    drainRemoteCandidates();
                }
            }
        }

        @Override
        public void onCreateFailure(final String error) {
            throw new RuntimeException("createSDP error: " + error);
        }

        @Override
        public void onSetFailure(final String error) {
            throw new RuntimeException("setSDP error: " + error);
        }

        private void drainRemoteCandidates() {
            for (IceCandidate candidate : queuedRemoteCandidates) {
                pc.addIceCandidate(candidate);
            }
            queuedRemoteCandidates = null;
        }
    }

    // Disconnect from remote resources, dispose of local resources, and exit.
    private void disconnectAndExit() {
        synchronized (quit[0]) {
            if (quit[0]) {
                return;
            }
            quit[0] = true;
            if (pc != null) {
                pc.dispose();
                pc = null;
            }
            // appRtcClient.sendMessage("{\"type\": \"bye\"}");
//            Response bye = Response
//                    .newBuilder()
//                    .setType(ResponseType.WEBRTC)
//                    .setWebrtcMsg(
//                            WebRTCMessage.newBuilder().setType(WebRTCType.BYE))
//                    .build();
//            base.sendMessage(bye);
        }
    }
  
    private MediaConstraints constraintsFromJSON(String jsonString) {
        try {
            MediaConstraints constraints = new MediaConstraints();
            JSONObject json = new JSONObject(jsonString);
            JSONObject mandatoryJSON = json.optJSONObject("mandatory");
            if (mandatoryJSON != null) {
                JSONArray mandatoryKeys = mandatoryJSON.names();
                if (mandatoryKeys != null) {
                    for (int i = 0; i < mandatoryKeys.length(); ++i) {
                        String key = (String) mandatoryKeys.getString(i);
                        String value = mandatoryJSON.getString(key);
                        constraints.mandatory.add(new MediaConstraints.KeyValuePair(key, value));
                    }
                }
            }
            JSONArray optionalJSON = json.optJSONArray("optional");
            if (optionalJSON != null) {
                for (int i = 0; i < optionalJSON.length(); ++i) {
                    JSONObject keyValueDict = optionalJSON.getJSONObject(i);
                    String key = keyValueDict.names().getString(0);
                    String value = keyValueDict.getString(key);
                    constraints.optional.add(new MediaConstraints.KeyValuePair(key, value));
                }
            }
            return constraints;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    // Return the list of ICE servers described by a WebRTCPeerConnection
    // configuration string.
    private LinkedList<PeerConnection.IceServer> iceServersFromPCConfigJSON(String pcConfig) {
        try {
            JSONObject json = new JSONObject(pcConfig);
            JSONArray servers = json.getJSONArray("iceServers");
            LinkedList<PeerConnection.IceServer> ret = new LinkedList<PeerConnection.IceServer>();
            for (int i = 0; i < servers.length(); ++i) {
                JSONObject server = servers.getJSONObject(i);
                String url = server.getString("url");
                String credential = server.has("credential") ? server.getString("credential") : "";
                ret.add(new PeerConnection.IceServer(url, "", credential));
            }
            return ret;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
  
}
