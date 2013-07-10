/*
 * Copyright 2012-2013 The MITRE Corporation, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this work except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mitre.svmp.events;
import java.io.IOException;

/*
 *struct svmp_fbstream_event_t {
 *        int cmd;
 *        long sessid; [> future use <]
 *};
 * 
 *[> sent to initialize a new stream <]
 *struct svmp_fbstream_init_t {
 *        char Gdev[20];
 *        char Adev[20];
 *        char IP[16];
 *        int vidport;
 *        int audport;
 *};
 *
 */

public class FbStreamEventMessage {
	public static final int START = 1;
	public static final int PLAY = 2;
	public static final int PAUSE = 3;
	public static final int STOP = 4;
	public static final int PLAYSDP = 5;
	public int cmd; 
	public long sessid;
	
	public FbStreamEventMessage(int cmd, String IP, int port) {
		this.cmd = cmd;
		this.IP = IP;
		this.vidport = port;
	}

	public String Gdev;
	public String Adev;
	public String IP;
	public int vidport;
	public int audport;
	public String SDP;
}
