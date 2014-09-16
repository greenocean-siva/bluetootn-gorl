/*
   Copyright 2012 Wolfgang Koller - http://www.gofg.at/

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package org.apache.cordova.plugin;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.Plugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;

public class BluetoothPlugin extends Plugin {
	private static final String ACTION_ENABLE = "enable";
	private static final String ACTION_DISABLE = "disable";
	private static final String ACTION_DISCOVERDEVICES = "discoverDevices";
	private static final String ACTION_GETUUIDS = "getUUIDs";
	private static final String ACTION_GETBONDEDDEVICES = "getBondedDevices";
	private static final String ACTION_CONNECT = "connect";
	private static final String ACTION_READ = "read";
	private static final String ACTION_READ2 = "read2";
	private static final String ACTION_READ3 = "read3";
	private static final String ACTION_READ4 = "read4";
	private static final String ACTION_WRITE = "write";
	private static final String ACTION_READ5 = "read5";

	private static final String ACTION_DISCONNECT = "disconnect";

	private static String ACTION_UUID = "";
	private static String EXTRA_UUID = "";

	private BluetoothAdapter m_bluetoothAdapter = null;
	private BPBroadcastReceiver m_bpBroadcastReceiver = null;
	private boolean m_discovering = false;
	private boolean m_gettingUuids = false;
	private boolean m_discoverable = false;
	private boolean m_stateChanging = false;
	 private AcceptThread mAcceptThread;
	private JSONArray m_discoveredDevices = null;
	private JSONArray m_gotUUIDs = null;
    private static final String NAME = "BluetoothListen";

    // Unique UUID for this application
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    BluetoothSocket bluetoothListenSocket = null;
	private ArrayList<BluetoothSocket> m_bluetoothSockets = new ArrayList<BluetoothSocket>();

	/**
	 * Constructor for Bluetooth plugin
	 */
	public BluetoothPlugin() {
		m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		m_bpBroadcastReceiver = new BPBroadcastReceiver();

		try {
			Field actionUUID = BluetoothDevice.class.getDeclaredField("ACTION_UUID");
			BluetoothPlugin.ACTION_UUID = (String) actionUUID.get(null);
			Log.d("BluetoothPlugin", "actionUUID: " + actionUUID.getName() + " / " + actionUUID.get(null));

			Field extraUUID = BluetoothDevice.class.getDeclaredField("EXTRA_UUID");
			BluetoothPlugin.EXTRA_UUID = (String) extraUUID.get(null);
			Log.d("BluetoothPlugin", "extraUUID: " + extraUUID.getName() + " / " + extraUUID.get(null));
		}
		catch( Exception e ) {
			Log.e("BluetoothPlugin", e.getMessage() );
		}
	}
	   public synchronized void start() {
        Log.d("BluetoothPlugin", "start");


	        // Start the thread to listen on a BluetoothServerSocket
	        if (mAcceptThread == null) {
	            mAcceptThread = new AcceptThread();
	            mAcceptThread.start();
	        }
	     //   setState(STATE_LISTEN);
	    }
	/**
	 * Register receiver as soon as we have the context
	 */
	@Override
	public void setContext(CordovaInterface ctx) {
		super.setContext(ctx);

		// Register for necessary bluetooth events
		ctx.getActivity().registerReceiver(m_bpBroadcastReceiver, new IntentFilter(
				BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
		ctx.getActivity().registerReceiver(m_bpBroadcastReceiver, new IntentFilter(
				BluetoothDevice.ACTION_FOUND));
		ctx.getActivity().registerReceiver(m_bpBroadcastReceiver, new IntentFilter(BluetoothPlugin.ACTION_UUID));
		//ctx.registerReceiver(m_bpBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
	}

	/**
	 * Execute a bluetooth function
	 */
	@SuppressWarnings({ "null", "deprecation" })
	@Override
	public PluginResult execute(String action, JSONArray args, String callbackId) {
		PluginResult pluginResult = null;

		//Log.d("BluetoothPlugin", "Action: " + action);

		// Check if bluetooth is supported at all
		if( m_bluetoothAdapter == null ) {
			pluginResult = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION, "No bluetooth adapter found");
		}
		else {
			if (ACTION_ENABLE.equals(action)) {
				// Check if bluetooth isn't disabled already
				if( !m_bluetoothAdapter.isEnabled() ) {
					m_stateChanging = true;
					ctx.startActivityForResult(this, new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1);
					while(m_stateChanging) {};

				}

				// Check if bluetooth is enabled now
				if(m_bluetoothAdapter.isEnabled()) {
          //start();
					pluginResult = new PluginResult(PluginResult.Status.OK, "OK");
				}
				else {
					pluginResult = new PluginResult(PluginResult.Status.ERROR, "Bluetooth not enabled");
				}
			}
			// Want to disable bluetooth?
			else if (ACTION_DISABLE.equals(action)) {
				if( !m_bluetoothAdapter.disable() && m_bluetoothAdapter.isEnabled() ) {
					pluginResult = new PluginResult(PluginResult.Status.ERROR, "Unable to disable bluetooth");
				}
				else {
					pluginResult = new PluginResult(PluginResult.Status.OK, "OK");
				}

			}
			else if (ACTION_DISCOVERDEVICES.equals(action)) {
				m_discoveredDevices = new JSONArray();

				if (!m_bluetoothAdapter.startDiscovery()) {
					pluginResult = new PluginResult(PluginResult.Status.ERROR,
							"Unable to start discovery");
				} else {
					m_discovering = true;

					// Wait for discovery to finish
					while (m_discovering) {}

					Log.d("BluetoothPlugin", "DiscoveredDevices: " + m_discoveredDevices.length());

					pluginResult = new PluginResult(PluginResult.Status.OK, m_discoveredDevices);
				}
			}
			// Want to list UUIDs of a certain device
			else if( ACTION_GETUUIDS.equals(action) ) {

				try {
					String address = args.getString(0);
					Log.d("BluetoothPlugin", "Listing UUIDs for: " + address);

					// Fetch UUIDs from bluetooth device
					BluetoothDevice bluetoothDevice = m_bluetoothAdapter.getRemoteDevice(address);
					Method m = bluetoothDevice.getClass().getMethod("fetchUuidsWithSdp");
					Log.d("BluetoothPlugin", "Method: " + m);
					m.invoke(bluetoothDevice);

					m_gettingUuids = true;

					while(m_gettingUuids) {}

					pluginResult = new PluginResult(PluginResult.Status.OK, m_gotUUIDs);

				}
				catch( Exception e ) {
					Log.e("BluetoothPlugin", e.toString() + " / " + e.getMessage() );

					pluginResult = new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
				}
			}
			else if ( ACTION_GETBONDEDDEVICES.equals(action) )
			{
				JSONArray bondedDevices = new JSONArray();
				Log.d( "BluetoothPlugin", "Getting Bonded List..." );
				Set<BluetoothDevice> bondSet =
						m_bluetoothAdapter.getBondedDevices();
				for (Iterator<BluetoothDevice> it = bondSet.iterator(); it.hasNext();) {
					BluetoothDevice bluetoothDevice = (BluetoothDevice) it.next();
					JSONObject deviceInfo = new JSONObject();
					try {
						deviceInfo.put("name", bluetoothDevice.getName());
						deviceInfo.put("address", bluetoothDevice.getAddress());
						deviceInfo.put("isBonded", true);
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					bondedDevices.put(deviceInfo);
					pluginResult = new PluginResult(PluginResult.Status.OK, bondedDevices);
				}
			}
			// Connect to a given device & uuid endpoint
			else if( ACTION_CONNECT.equals(action) ) {
				try {
					String address = args.getString(0);
					UUID uuid = UUID.fromString(args.getString(1));
					//UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

					Log.d( "BluetoothPlugin", "Connecting..." );

					BluetoothDevice bluetoothDevice = m_bluetoothAdapter.getRemoteDevice(address);
					BluetoothSocket bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);

					bluetoothSocket.connect();

					m_bluetoothSockets.add(bluetoothSocket);
					int socketId = m_bluetoothSockets.indexOf(bluetoothSocket);

					pluginResult = new PluginResult(PluginResult.Status.OK, socketId);
				}
				catch( Exception e ) {
					Log.e("BluetoothPlugin", e.toString() + " / " + e.getMessage() );

					pluginResult = new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
				}
			}
			else if( ACTION_READ.equals(action) ) {
				try {
					int socketId = args.getInt(0);

					//Log.d( "BluetoothPlugin", "Get Data..." );

					BluetoothSocket bluetoothSocket = m_bluetoothSockets.get(socketId);
					InputStream inputStream = bluetoothSocket.getInputStream();
					Calendar cal = Calendar.getInstance();

			    	Date startTime = cal.getTime();
					byte[] buffer = new byte[1024];
//					char [] buffer = new char[1024];
					String recvdString="";
					int i=0;
					int k=0;
					int byteCnt=0;
					boolean j=true;
					char buf = 0;
					boolean timeOut=false;
					while (j)
					{
						Calendar newCal = Calendar.getInstance();
						Date endTime = newCal.getTime();

						if ((endTime.getTime()-startTime.getTime())<60000)
						{

								if (inputStream.available()>0)
								{
								//	Log.d( "BluetoothPlugin", "Time Increment: " + format.format(endTime));

									i += inputStream.read(buffer,k,inputStream.available());
									k=i;
									Log.d( "BluetoothPlugin", "i="+i);
									buf = (char)(buffer[i-1]&0xFF);
									Log.d( "BluetoothPlugin", "buf="+Integer.toHexString(buffer[i-1]&0xFF));
									if ((buf== '#') || (buf==0x0A)|| (buf==(char)0xBB)|| (buf==(char)0xAA))
									{
										//if (timeOut == true) Log.d( "BluetoothPlugin", "Time Out");
										j=false;
									}
								}

						}
						else
						{
							timeOut=true;
							j=false;
						}
/*
						 buffer[i]=  (char) inputStream.read();

							if ((buffer[i] == '#') || (buffer[i]==0x0A))
							{
								j=false;
							}
								i++;
*/
					}
					if (timeOut)
					{
						Log.d( "BluetoothPlugin", "Time Out");
						recvdString = "Timeout";
					}
					else
					{
							byteCnt = i;

							recvdString= new String(buffer,0,i);//.toString();//"KBytes" + byteCnt;
							i=0;
							String stringByteCnt = String.valueOf(byteCnt);



					}

					//buffer = b.toString();
					Log.d( "BluetoothPlugin", "String: " + recvdString );
					pluginResult = new PluginResult(PluginResult.Status.OK,recvdString);
				}
				catch( Exception e ) {
					Log.e("BluetoothPlugin", e.toString() + " / " + e.getMessage() );

					pluginResult = new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
				}
			}
			else if( ACTION_READ2.equals(action) ) {
				try {

					int socketId = args.getInt(0);
					Calendar cal = Calendar.getInstance();

			    	Date startTime = cal.getTime();

					//Log.d( "BluetoothPlugin", "Get Data..." );

					BluetoothSocket bluetoothSocket = m_bluetoothSockets.get(socketId);
					InputStream inputStream = bluetoothSocket.getInputStream();
//					DataInputStream dataInputStream = new DataInputStream(inputStream);

					//char[] buffer = new char[15000];
					byte [] buf = new byte[55000];
					//byte[] buffer2 = new byte[128];
					String recvdString="";
					int i=0;
					int k=0;
					int byteCnt=0;
					boolean j=true;
					 SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
					 Log.d( "BluetoothPlugin", "StartTime: " + format.format(startTime));
					boolean timeOut = false;
					while (j)
					{
						Calendar newCal = Calendar.getInstance();
						Date endTime = newCal.getTime();

						if ((endTime.getTime()-startTime.getTime())<12000)
						{

							if (inputStream.available()>0)
								{
								//	Log.d( "BluetoothPlugin", "Time Increment: " + format.format(endTime));
									i += inputStream.read(buf,k,inputStream.available());
									k=i;
									Log.d( "BluetoothPlugin", "i="+i);
								}
							//Log.d( "BluetoothPlugin", "i="+dataInputStream);
							//inputStream.close();
							if (i>51180)
							{
								//Log.d( "BluetoothPlugin", "i="+i);
								j= false;
							//i++;
							}
						}
						else
						{
							j=false;
							timeOut = true;
							Log.d( "BluetoothPlugin", "ECG Read TimeOut");
						}



					}
					if (timeOut)
					{
						recvdString= "Aborted";
					}
					else
					{
						File ecgPath = Environment.getExternalStorageDirectory();
						File ecg = new File (ecgPath,"/prago/ecg.txt");
						FileWriter fos = new FileWriter(ecg,false);

						String stringBuf = new String("");
						//long byteCnt
						byteCnt = (i-1)/3;
						long[] buf2 = new long[byteCnt];

						for (k=0;k<byteCnt;k++)
							{

							 int firstByte = 0;
							int secondByte = 0;
							int thirdByte = 0;
							int fourthByte = 0;
							int index = k*3;
							firstByte = (0x000000FF & ((int)buf[index+1]));
							secondByte = (0x000000FF & ((int)buf[index+2]));
							thirdByte = (0x000000FF & ((int)buf[index+3]));
							buf2[k]= ((long) (firstByte << 16
								        | secondByte << 8
							                | thirdByte
							               ))
							               & 0xFFFFFFFFL;

							stringBuf = buf2[k] + ",";
							fos.write(stringBuf);
							}


						fos.flush();
						fos.close();
								byteCnt = i;


							recvdString=  ecg.getPath();
					}
							i=0;


					pluginResult = new PluginResult(PluginResult.Status.OK,recvdString);
				}
				catch( Exception e ) {
					Log.e("BluetoothPlugin", e.toString() + " / " + e.getMessage() );

					pluginResult = new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
				}
			}

			else if( ACTION_READ3.equals(action) ) {
				try {
					int socketId = args.getInt(0);

					Log.d( "BluetoothPlugin", "Get Steth Data..." );

					BluetoothSocket bluetoothSocket = m_bluetoothSockets.get(socketId);
					//bluetoothSocket.close();
					//bluetoothSocket = m_bluetoothSockets.get(socketId);
					//bluetoothSocket.connect();
					InputStream inputStream = bluetoothSocket.getInputStream();
					//inputStream.reset();
					//int server_port = 9999;
					//DatagramSocket s = new DatagramSocket();
					//InetAddress local = InetAddress.getByName("192.168.2.7");
					//s.connect(local,server_port);
					//int msg_length=messageStr.length();
					//byte[] message = messageStr.getBytes();

					//char[] buffer = new char[15000];
					//byte [] buf = new byte[10000];
					//byte[] buffer2 = new byte[128];
//					String recvdString;
					Calendar cal = Calendar.getInstance();
					//byte [] buf = new byte[245000];
			    	Date startTime = cal.getTime();
					String recvdString= "";
					int i=0;
					int endofFileDetect=0;
					byte [] firstChar = new byte[1];
					int writetoFile=0;
					int k=0;
					long finalbytes=0;
					boolean startdetect = false;
					int byteCnt=0;
					boolean j=true;
					boolean ecgRec = false;
					byte [] buf = new byte[10000];
					firstChar[0] = 0x52;
					File stethPath = Environment.getExternalStorageDirectory();
					File steth = new File (stethPath,"/prago/steth.wav");
					FileOutputStream fos = new FileOutputStream(steth);
					while (j)
					{
						Calendar newCal = Calendar.getInstance();
						Date endTime = newCal.getTime();
						if ((endTime.getTime()-startTime.getTime())<90000)
						{
							if (inputStream.available()>0)
							{
								//Log.d( "BluetoothPlugin", "inputStream.available="+inputStream.available());
								//byte [] buf = new byte[inputStream.available()];
								k = inputStream.read(buf,0,inputStream.available());
								//Log.d( "BluetoothPlugin", "buf[0]="+buf[0]);
								if((writetoFile == 0))
								{
									if((buf[0]&0xFF)== 0x52)
									{
										if (k>1)
										{
											if ((buf[1]&0xFF) == 0x49)
											{
												writetoFile = 1;
												i=0;
											}

										}
										else
										{
											startdetect = true;
										}

									}
									else if (((buf[0]&0xFF)== 0x49) && startdetect == true)
									{
										fos.write(firstChar,0,1);
										writetoFile = 1;
										i=0;
									}
									else
									{
										startdetect = false;
									}
								}
								if (writetoFile == 1)
								{

									i += k;
									//Log.d( "BluetoothPlugin", "i="+i);
									//Log.d( "BluetoothPlugin", "k="+k);
									fos.write(buf,0,k);
									//if (k>1)Log.d( "BluetoothPlugin", "buf[k-2]="+Integer.toHexString(buf[k-2]&0xFF));
									//Log.d( "BluetoothPlugin", "buf[k-1]="+Integer.toHexString(buf[k-1]&0xFF));
									if ((k>1) && ((buf[k-2]&0xFF)==0xAA) && ((buf[k-1]&0xFF)==0xBB))
										{
											endofFileDetect = 2;
										//	Log.d( "BluetoothPlugin", "EoF Detected Multibyte");

										}
									else if ((k==1) && ((buf[0]&0xFF) == 0xAA))
									{
										endofFileDetect = 1;
									//	Log.d( "BluetoothPlugin", "EoF Detected Firstbyte");
									}
									else if (((buf[0]&0xFF)==0xBB)  && (endofFileDetect ==1))
											{
												endofFileDetect += 1;
										//		Log.d( "BluetoothPlugin", "EoF Detected Sectbyte");
											}
									else
									{
										endofFileDetect = 0;
									}



										if (endofFileDetect == 2)
										{
											Log.d( "BluetoothPlugin", "File Write Complete");
											//Log.d( "BluetoothPlugin", "i="+i);
											fos.flush();
											fos.close();
											j= false;
										//i++;
										 recvdString= steth.getPath();

										}



								}
							//	DatagramPacket p = new DatagramPacket(buf, k,local,server_port);
							//	s.send(p);//					DataInputStream dataInputStream = new DataInputStream(inputStream);
							}
							//Log.d( "BluetoothPlugin", "i="+dataInputStream);
							//inputStream.close();

						}
						else
						{
							j=false;
							//timeOut=true;
							Log.d( "BluetoothPlugin", "Steth Read TimeOut");
							//bluetoothSocket.close();
							// recvdString= "Aborted";
								fos.flush();
								fos.close();
								recvdString= steth.getPath();
						}

					}
					pluginResult = new PluginResult(PluginResult.Status.OK,recvdString);
				}
				catch( Exception e ) {

					Log.e("BluetoothPlugin", e.toString() + " / " + e.getMessage() );

					pluginResult = new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
				}
			}

			//--change--//

else if( ACTION_READ5.equals(action) ) {
				try {
					int socketId = args.getInt(0);

					Log.d( "BluetoothPlugin", "Transfer Steth Data..." );

					BluetoothSocket bluetoothSocket = m_bluetoothSockets.get(socketId);
					//bluetoothSocket.close();
					//bluetoothSocket = m_bluetoothSockets.get(socketId);
					//bluetoothSocket.connect();
					InputStream inputStream = bluetoothSocket.getInputStream();
					//inputStream.reset();
					//int server_port = 9999;
					//DatagramSocket s = new DatagramSocket();
					//InetAddress local = InetAddress.getByName("192.168.2.7");
					//s.connect(local,server_port);
					//int msg_length=messageStr.length();
					//byte[] message = messageStr.getBytes();

					//char[] buffer = new char[15000];
					//byte [] buf = new byte[10000];
					//byte[] buffer2 = new byte[128];
					//String recvdString;
					Calendar cal = Calendar.getInstance();
					//byte [] buf = new byte[245000];
			    	Date startTime = cal.getTime();
					String recvdString= "";
					int i=0;
					int endofFileDetect=0;
					byte [] firstChar = new byte[1];
					int writetoFile=0;
					int k=0;
					long finalbytes=0;
					boolean startdetect = false;
					int byteCnt=0;
					boolean j=true;
					boolean ecgRec = false;
					byte [] buf = new byte[10000];
					firstChar[0] = 0x52;
					File stethPath = Environment.getExternalStorageDirectory();
					File steth = new File (stethPath,"/prago/steth.wav");
					FileOutputStream fos = new FileOutputStream(steth);
					while (j)
					{
						Calendar newCal = Calendar.getInstance();
						Date endTime = newCal.getTime();
						if ((endTime.getTime()-startTime.getTime())<5000)
						{
							if (inputStream.available()>0)
							{
//								Log.d( "BluetoothPlugin", "inputStream.available="+inputStream.available());
						cal = Calendar.getInstance();
						startTime = cal.getTime();
								//byte [] buf = new byte[inputStream.available()];
								k = inputStream.read(buf,0,inputStream.available());
								//Log.d( "BluetoothPlugin", "buf[0]="+buf[0]);
								if((writetoFile == 0))
								{
									if((buf[0]&0xFF)== 0x52)
									{
										if (k>1)
										{
											if ((buf[1]&0xFF) == 0x49)
											{
												writetoFile = 1;
												i=0;
											}

										}
										else
										{
											startdetect = true;
										}

									}
									else if (((buf[0]&0xFF)== 0x49) && startdetect == true)
									{
										fos.write(firstChar,0,1);
										writetoFile = 1;
										i=0;
									}
									else
									{
										startdetect = false;
									}
								}
								if (writetoFile == 1)
								{

									i += k;
									//Log.d( "BluetoothPlugin", "i="+i);
									//Log.d( "BluetoothPlugin", "k="+k);
									fos.write(buf,0,k);
									//if (k>1)Log.d( "BluetoothPlugin", "buf[k-2]="+Integer.toHexString(buf[k-2]&0xFF));
									//Log.d( "BluetoothPlugin", "buf[k-1]="+Integer.toHexString(buf[k-1]&0xFF));
									if ((k>1) && ((buf[k-2]&0xFF)==0xAA) && ((buf[k-1]&0xFF)==0xBB))
										{
											endofFileDetect = 2;
										//	Log.d( "BluetoothPlugin", "EoF Detected Multibyte");

										}
									else if ((k==1) && ((buf[0]&0xFF) == 0xAA))
									{
										endofFileDetect = 1;
									//	Log.d( "BluetoothPlugin", "EoF Detected Firstbyte");
									}
									else if (((buf[0]&0xFF)==0xBB)  && (endofFileDetect ==1))
											{
												endofFileDetect += 1;
										//		Log.d( "BluetoothPlugin", "EoF Detected Sectbyte");
											}
									else
									{
										endofFileDetect = 0;
									}



										if (endofFileDetect == 2)
										{
											Log.d( "BluetoothPlugin", "File Write Complete");
											//Log.d( "BluetoothPlugin", "i="+i);
											fos.flush();
											fos.close();
											j= false;
										//i++;
										 recvdString= steth.getPath();

										}



								}
							//	DatagramPacket p = new DatagramPacket(buf, k,local,server_port);
							//	s.send(p);//					DataInputStream dataInputStream = new DataInputStream(inputStream);
							}
							//Log.d( "BluetoothPlugin", "i="+dataInputStream);
							//inputStream.close();

						}
						else
						{
							j=false;
							//timeOut=true;
							Log.d( "BluetoothPlugin", "Steth Read TimeOut");
							//bluetoothSocket.close();
							// recvdString= "Aborted";
								fos.flush();
								fos.close();
								recvdString= steth.getPath();
						}

					}
					pluginResult = new PluginResult(PluginResult.Status.OK,recvdString);
				}
				catch( Exception e ) {

					Log.e("BluetoothPlugin", e.toString() + " / " + e.getMessage() );

					pluginResult = new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
				}
			}

		//--change--//


			else if( ACTION_READ4.equals(action) ) {
				try {
					start();

		//			int socketId = args.getInt(0);
					Log.d( "BluetoothPlugin", "Make Discoverable" );
					 BluetoothAdapter mBluetoothAdapter = null;
		            ctx.startActivityForResult(this, new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE), 1);
				            m_discoverable=true;
					Calendar cal = Calendar.getInstance();

		    		Date startTime = cal.getTime();
Calendar newCal = Calendar.getInstance();
				String recvdString= "";
				Date endTime = newCal.getTime();
		            	while(m_discoverable && ((endTime.getTime()-startTime.getTime())<32000)){
					newCal = Calendar.getInstance();
					endTime = newCal.getTime();
				}
				if (m_discoverable)
				{
					recvdString = "No Device";
				}
				else
				{
					Log.d( "BluetoothPlugin", "Connected with Remote Device" );

					BluetoothSocket bluetoothSocket = bluetoothListenSocket;
					InputStream inputStream = bluetoothSocket.getInputStream();

					int i=0;
					int k=0;
					boolean j=true;
					boolean measurementComplete = false;
//					boolean measurementOngoing = false;
					boolean measurementStart = false;
          float decweight = 0;
					int [] buf = new int[100];
					while(!measurementComplete){
								buf[i]=  inputStream.read();

								if ((i>5) && (buf[i] == 0x02) && (buf[i-6]==0x93) && (buf[i-1]==0x00) && !measurementStart)
								{
									measurementStart=true;
								}
								if (measurementStart && (buf[i-1]==0x04) && (buf[i-7]==0x93) && (buf[i-2]==0x0))
								{
									measurementComplete = true;
									measurementStart = false;
//									measurementOngoing = false;
      					  decweight = (buf[i-10]<<8) + buf[i-9];
								}
									i++;

								Log.d( "BluetoothPlugin", "i="+i);
							}

						//	String recvdString= new String(buf,0,i,"ISO-8859-1");;//new String(buf,0,i,"ISO-8859-1");//.toString();//"KBytes" + byteCnt;
						float weight = decweight/100;
						//weight += decweight/100;
							recvdString= "" + weight;
							bluetoothSocket.close();
							Log.d( "BluetoothPlugin", "Disconnected with Remote Device" );
				}
					pluginResult = new PluginResult(PluginResult.Status.OK,recvdString);

				}
				catch( Exception e ) {
					Log.e("BluetoothPlugin", e.toString() + " / " + e.getMessage() );

					pluginResult = new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
				}
			}
			else if( ACTION_WRITE.equals(action) ) {
				try {

					int socketId = args.getInt(0);
					byte[] value = 	{0x11, 0x0d, 0x44, 0x4d, 0x50};
//					byte[] value = 	{(byte)0x11,(byte)0x0D, (byte)0x0A, (byte)0x44, (byte)0x4D, (byte)0x46};

					String string = new String(value);
					char sendCmd = 'g';
					byte sendCmdByte = (byte) sendCmd;//.getBytes("UTF-16LE");
					byte[] data = args.getString(1).getBytes("UTF-8");

					if (data[0] == sendCmdByte)
					{
						data = value;
						Log.d( "BluetoothPlugin", "Sending Onetouch Ultra2 Commands..." );
					}
					else if (data[0] == 'e')
					{
						data = args.getString(1).getBytes("UTF-8");
						//Log.d( "BluetoothPlugin", "Sending +tronic Commands..." + args.getString(1));
					}
					else
					{
						data = args.getString(1).getBytes("UTF-16LE");
						//Log.d( "BluetoothPlugin", "Sending +tronic Commands..." + args.getString(1));
					}
					//Log.d( "BluetoothPlugin", "Write Data..." + string );

					BluetoothSocket bluetoothSocket = m_bluetoothSockets.get(socketId);
					OutputStream  outputStream = bluetoothSocket.getOutputStream();


					outputStream.write(data);
					outputStream.flush();
					//outputStream.close();
					//Log.d( "BluetoothPlugin", "Buffer: " + String.valueOf(buffer) );
					pluginResult = new PluginResult(PluginResult.Status.OK, "Success");
				}
				catch( Exception e ) {
					Log.e("BluetoothPlugin", e.toString() + " / " + e.getMessage() );

					pluginResult = new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
				}
			}
			else if( ACTION_DISCONNECT.equals(action) ) {
				try {
					int socketId = args.getInt(0);

					// Fetch socket & close it
					BluetoothSocket bluetoothSocket = m_bluetoothSockets.get(socketId);
					bluetoothSocket.close();

					// Remove socket from internal list
					m_bluetoothSockets.remove(socketId);

					// Everything went fine...
					pluginResult = new PluginResult(PluginResult.Status.OK, "OK");
				}
				catch( Exception e ) {
					Log.e("BluetoothPlugin", e.toString() + " / " + e.getMessage() );

					pluginResult = new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
				}
			}
			else {
				pluginResult = new PluginResult(PluginResult.Status.INVALID_ACTION, "Action '" + action + "' not supported");
			}
		}

		return pluginResult;
	}

	/**
	 * Receives activity results
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if( requestCode == 1 ) {
			m_stateChanging = false;
		}
	}

	/**
	 * Helper class for handling all bluetooth based events
	 */
	private class BPBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			//Log.d( "BluetoothPlugin", "Action: " + action );

			// Check if we found a new device
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice bluetoothDevice = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

				try {
					JSONObject deviceInfo = new JSONObject();
					deviceInfo.put("name", bluetoothDevice.getName());
					deviceInfo.put("address", bluetoothDevice.getAddress());

					m_discoveredDevices.put(deviceInfo);
				} catch (JSONException e) {
					Log.e("BluetoothPlugin", e.getMessage());
				}
			}
			// Check if we finished discovering devices
			else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				m_discovering = false;
			}
			// Check if we found UUIDs
			else if(BluetoothPlugin.ACTION_UUID.equals(action)) {
				m_gotUUIDs = new JSONArray();

				Parcelable[] parcelUuids = intent.getParcelableArrayExtra(BluetoothPlugin.EXTRA_UUID);
				if( parcelUuids != null ) {
					Log.d("BluetoothPlugin", "Found UUIDs: " + parcelUuids.length);

					// Sort UUIDs into JSON array and return it
					for( int i = 0; i < parcelUuids.length; i++ ) {
						m_gotUUIDs.put( parcelUuids[i].toString() );
					}

					m_gettingUuids = false;
				}
			}
		}
	};

	private class AcceptThread extends Thread {
	    // The local server socket
	    private final BluetoothServerSocket mmServerSocket;

	    public AcceptThread() {
	        BluetoothServerSocket tmp = null;

	        // Create a new listening server socket
	        try {
	            tmp = m_bluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
	        } catch (IOException e) {
//	            Log.e(TAG, "listen() failed", e);
	        }
	        mmServerSocket = tmp;
	    }

	    public void run() {
	        Log.d("BluetoothPlugin", "BEGIN mAcceptThread" + this);
	        setName("AcceptThread");
	        BluetoothSocket socket = null;
	        boolean mstate=true;
	        // Listen to the server socket if we're not connected
	        while (mstate) {
	            try {
	                // This is a blocking call and will only return on a
	                // successful connection or an exception
	                socket = mmServerSocket.accept();
	            } catch (IOException e) {
//	                Log.e(TAG, "accept() failed", e);
	                break;
	            }

	            // If a connection was accepted
	            if (socket != null) {

	            	connected(socket, socket.getRemoteDevice());
	                break;

	            }
	        }
	       // if (D) Log.i(TAG, "END mAcceptThread");
	    }

	    public void cancel() {
	     //   if (D) Log.d(TAG, "cancel " + this);
	        try {
	            mmServerSocket.close();
	        } catch (IOException e) {
	       //     Log.e(TAG, "close() of server failed", e);
	        }
	    }
	}
	public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
       // Cancel the accept thread because we only want to connect to one device
        if (mAcceptThread != null) {mAcceptThread.cancel(); mAcceptThread = null;}
        bluetoothListenSocket = socket;
        // Start the thread to manage the connection and perform transmissions
        m_discoverable = false;
    }
}
/**
 * This thread runs while listening for incoming connections. It behaves
 * like a server-side client. It runs until a connection is accepted
 * (or until cancelled).
 */
