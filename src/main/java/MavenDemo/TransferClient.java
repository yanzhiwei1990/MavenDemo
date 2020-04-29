package MavenDemo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.rmi.dgc.Lease;
import java.util.concurrent.ExecutorService;

import org.json.JSONObject;
import org.omg.CORBA.PUBLIC_MEMBER;

import MavenDemo.TcpClient.TransferClientCallback;
import MavenDemo.TransferServer.ClientCallback;

public class TransferClient {

	public static final String TAG = TransferClient.class.getSimpleName() + " : %s\n";
	
	private Socket mClientSocket = null;
	private TransferClient mDestinationClient = null;
	private TransferServer mTransferServer = null;
	private ClientCallback mClientCallback = null;
	private ExecutorService mExecutorService = null;
	private InputStream mInputStream = null;
	private OutputStream mOutputStream = null;
	private BufferedInputStream mSocketReader = null;
	private BufferedOutputStream mSocketWriter = null;
	private JSONObject mClientInfomation = null;//add mac address as name
	private JSONObject mClientStatus = null;//online offline
	private boolean isRunning = false;
	private InetAddress mRemoteAddress = null;
	private InetAddress mLocalAddress = null;
	private boolean mRecognised = false;
	private String mClientRole = null;
	private TransferClientCallback mTransferClientCallback = null;
	
	public static final String ROLE_REQUEST = "request";
	public static final String ROLE_REPONSE = "response";
	
	private Runnable mStartListener = new Runnable() {

		public void run() {
			Log.PrintLog(TAG, "startListener running");
			try {
				mInputStream = mClientSocket.getInputStream();
			} catch (IOException e) {
				Log.PrintError(TAG, "accept getInputStream Exception = " + e.getMessage());
			}
			try {
				mOutputStream = mClientSocket.getOutputStream();
			} catch (IOException e) {
				Log.PrintError(TAG, "accept getOutputStream Exception = " + e.getMessage());
			}
			if (mInputStream != null && mOutputStream != null) {
				String inMsg = null;
				String outMsg = null;
				byte[] buffer = new byte[1024 * 1024];
				int length = -1;
				mSocketReader = new BufferedInputStream(mInputStream, buffer.length);
				mSocketWriter = new BufferedOutputStream(mOutputStream, buffer.length);

				while (isRunning) {
					try {
					    while ((length = mSocketReader.read(buffer, 0, buffer.length)) != -1) {
					    	if (length <= 1024) {
					    		try {
					    			inMsg = new String(buffer, 0, length, Charset.forName("UTF-8")).trim();
								} catch (Exception e) {
									inMsg = null;
									Log.PrintError(TAG, "parse first 256 bytes error");
								}
					    		outMsg = dealCommand(inMsg);
					    		if (!"no_need_feedback".equals(outMsg)) {
					    			Log.PrintLog(TAG, "Received from inMsg = " + inMsg + ", outMsg = " + outMsg);
					    			JSONObject result = new JSONObject();
							    	result.put("command", "status");
							    	result.put("status", outMsg);
							    	sendMessage(result.toString());
					    		} else {
					    			outMsg = "unknown";
					    		}
					    	} else {
					    		outMsg = "unknown";
					    	}
					    	Log.PrintLog(TAG, "length = " + length + ", inMsg = " + inMsg + ",outMsg = " + outMsg);
					    	if (!mRecognised) {
					    		mRecognised = true;
					    		parseClientRole(outMsg);
					    		if (mClientCallback != null) {
					    			mClientCallback.onClientConnect(TransferClient.this, null);
					    		}
					    		Log.PrintLog(TAG, "Received Recognised mClientInfomation = " + mClientInfomation);
					    	}
					    	if ("unknown".equals(outMsg)) {
					    		//need to transfer
					    		if (ROLE_REPONSE.equals(mClientRole)) {
					    			if (mDestinationClient == null) {
					    				mDestinationClient = mTransferServer.getTransferClient(ROLE_REQUEST, getRequestClientInetAddress(), getRequestClientPort());
					    			}
					    			if (mDestinationClient != null) {
					    				mDestinationClient.transferBuffer(buffer, 0, length);
					    			}  else {
					    				Log.PrintLog(TAG, "Received no Recognised ROLE_REQUEST client");
					    				/*if (mTransferClientCallback != null) {
							    			JSONObject objCommand = new JSONObject();
							    			objCommand.put("command", "status");
							    			objCommand.put("role", mClientRole);
							    			objCommand.put("status", "request_missing");
							    			objCommand.put("address", getRemoteInetAddress());
							    			objCommand.put("port", getRemotePort());
							    			mTransferClientCallback.onTransferClientCommand(TransferClient.this, objCommand);
							    		}
					    				break;*/
					    			}
					    		} else {
					    			//request client need to wait for respponse client ready
					    			int count = 30;
					    			if (mDestinationClient == null) {
					    				if (mTransferClientCallback != null) {
					    					//request client in and tell response client to start connect to transfer server to transfer request
					    					/*
					    					{
					    						"command":"start_connect_transfer",
					    						"server_info":
					    							{
					    								"connected_transfer_server_address":"www.opendiylib.com",
					    								"connected_transfer_server_port":19920,
					    								"request_client_nat_address":"58.246.136.202",
					    								"request_client_nat_port":50000,
					    								"bonded_response_server_address","192.168.188.150"
					    								"bonded_response_server_port":19920
					    							}
					    					}
					    					*/
							    			JSONObject objCommand = new JSONObject();
							    			objCommand.put("command", "start_connect_transfer");
							    			JSONObject server_info = new JSONObject();
							    			server_info.put("connected_transfer_server_address", MainDemo.FIXED_HOST_SITE);
							    			server_info.put("connected_transfer_server_port", mTransferServer.getPort());
							    			server_info.put("request_client_nat_address", getRemoteInetAddress());
							    			server_info.put("request_client_nat_port", getRemotePort());
							    			server_info.put("bonded_response_server_address", mTransferServer.getBondedReponseAddress());
							    			server_info.put("bonded_response_server_port", mTransferServer.getBondedReponsePort());
							    			objCommand.put("server_info", server_info);
							    			mTransferClientCallback.onTransferClientCommand(TransferClient.this, objCommand);
							    		}
					    			}
					    			while (mDestinationClient == null) {
					    				delayMs(1000);
					    				mDestinationClient = mTransferServer.getTransferClient(ROLE_REPONSE, getRemoteInetAddress(), getRemotePort());
					    				count--;
					    				if (count < 0) {
					    					Log.PrintLog(TAG, "wait response client 30s time out");
					    					break;
					    				}
					    			}
					    			if (count < 0) {
					    				Log.PrintLog(TAG, "stop request client as time out");
					    				break;
					    			}
					    			if (mDestinationClient != null) {
					    				mDestinationClient.transferBuffer(buffer, 0, length);
					    			} else {
					    				Log.PrintLog(TAG, "Received no Recognised ROLE_REPONSE client");
					    				/*if (mTransferClientCallback != null) {
							    			JSONObject objCommand = new JSONObject();
							    			objCommand.put("command", "status");
							    			objCommand.put("role", mClientRole);
							    			objCommand.put("status", "request_response_timeout");
							    			objCommand.put("address", getRemoteInetAddress());
							    			objCommand.put("port", getRemotePort());
							    			mTransferClientCallback.onTransferClientCommand(TransferClient.this, objCommand);
							    		}
					    				break;*/
					    			}
					    		}
					    	}
					    }
					    Log.PrintLog(TAG, "startListener disconnect");
					   
					} catch(Exception e) {
						Log.PrintError(TAG, "accept Exception = " + e.getMessage());
						e.printStackTrace();
						break;
					}
					break;
				}
			} else {
				Log.PrintError(TAG, "accept get stream error");
			}
			Log.PrintLog(TAG, "stop accept");
			dealClearWork();
		}
	};
	
	public TransferClient(ExecutorService executor, TransferServer transferServer, Socket socket) {
		mClientSocket = socket;
		mExecutorService = executor;
		mTransferServer = transferServer;
		mRemoteAddress = socket.getInetAddress();
		mLocalAddress = socket.getLocalAddress();
		printClientInfo();
	}

	public void setClientCallback(ClientCallback callback) {
		mClientCallback = callback;
	}
	
	public void setTransferClientCallback(TransferClientCallback callback) {
		mTransferClientCallback = callback;
	}
	
	public void startListen() {
		Log.PrintLog(TAG, "startListen");
		isRunning = true;
		mExecutorService.submit(mStartListener);
	}
	
	public void stopListen() {
		Log.PrintLog(TAG, "stopListen");
		isRunning = false;
		closeSocket();
		if (mClientCallback != null) {
			mClientCallback.onClientDisconnect(this, null);
		}
	}
	
	public InputStream getClientInputStream() {
		return mInputStream;
	}
	
	public OutputStream getClientOutputStream() {
		return mOutputStream;
	}
	
	public JSONObject getClientInformation() {
		return mClientInfomation;
	}
	
	public String getRemoteInetAddress() {
		return mRemoteAddress.getHostAddress();
	}
	
	public int getRemotePort() {
		return mClientSocket.getPort();
	}
	
	public String getLocalInetAddress() {
		return mLocalAddress.getHostAddress();
	}
	
	public int getLocalPort() {
		return mClientSocket.getLocalPort();
	}

	public String getClientRole() {
		return mClientRole;
	}
	
	private void printClientInfo() {
		if (mClientInfomation != null && mClientInfomation.length() > 0) {
			Log.PrintLog(TAG, "printClientInfo:" + mClientInfomation);
		}
	}
	
	public String getRequestClientInetAddress() {
		String result = null;
		try {
			//connect to transfer server and report related infomation
			//{"command":"information","information":{"name":"response_tranfer_client","mac_address":"10-7B-44-15-2D-B6","dhcp_address":"192.168.188.150","dhcp_port":50001,"request_client_nat_address":"114.82.25.165","request_client_nat_port":50000,"connected_transfer_server_address":"opendiylib.com","connected_transfer_server_port":19911}}
			result = mClientInfomation.getString("request_client_nat_address");
		} catch (Exception e) {
			//Log.PrintError(TAG, "getRequestClientInetAddress Exception = " + e.getMessage());
		}
		return result;
	}
	
	public int getRequestClientPort() {
		int result = -1;
		try {
			result = mClientInfomation.getInt("request_client_nat_port");
		} catch (Exception e) {
			//Log.PrintError(TAG, "getRequestClientPort Exception = " + e.getMessage());
		}
		return result;
	}
	
	private void sendMessage(String outMsg) {
		try {
			if (mSocketWriter != null && outMsg != null && outMsg.length() > 0) {
				byte[] send = (outMsg/* + "\n"*/).getBytes(Charset.forName("UTF-8"));
				mSocketWriter.write(send, 0, send.length);
		    	mSocketWriter.flush();
			}
		} catch (Exception e) {
			Log.PrintError(TAG, "sendMessage Exception = " + e.getMessage());
		}
	}
	
	/*
	 * transfer server need to recognize request client and response client
	*/
	private String dealCommand(String data) {
		String result = "unknown";
		String command = null;
		JSONObject obj = null;
		if (data != null) {
			try {
				obj = new JSONObject(data);
			} catch (Exception e) {
				//Log.PrintError(TAG, "dealCommand new JSONObject Exception = " + e.getMessage());
			}
			//connect to transfer server and report related infomation
			//{"command":"information","information":{"name":"response_tranfer_client","mac_address":"10-7B-44-15-2D-B6","dhcp_address":"192.168.188.150","dhcp_port":50001,"request_client_nat_address":"114.82.25.165","request_client_nat_port":50000,"connected_transfer_server_address":"opendiylib.com","connected_transfer_server_port":19911}}

			if (obj != null && obj.length() > 0) {
				try {
					command = obj.getString("command");
				} catch (Exception e) {
					//Log.PrintError(TAG, "dealCommand getString command Exception = " + e.getMessage());
				}
				switch (command) {
					case "information":
						result = parseInformation(obj);
						break;
					case "status":
						result = parseStatus(obj);
						break;
					default:
						break;
				}
			}
		}
		return result;
	}
	
	private String parseInformation(JSONObject data) {
		String result = "unknown";
		if (data != null && data.length() > 0) {
			//connect to transfer server and report related infomation
			/*
			{
				"command":"information",
				"information":
					{
						"name":"response_request_client",
						"mac_address":"10-7B-44-15-2D-B6",
						"client_role":"request",
						"request_client_nat_address","58.246.136.202",
						"request_client_nat_port":5555,
						"dhcp_address","192.168.188.150",
						"dhcp_port":5555,
						"connected_transfer_server_address":"opendiylib.com",
						"connected_transfer_server_port":19910,
						"connected_server_address":"opendiylib.com",
						"connected_server_port":19920
					}
			}
			*/
			mClientInfomation = data.getJSONObject("information");
			try {
				if (mClientInfomation != null && mClientInfomation.length() > 0) {
					//add nat address
					mClientInfomation.put("client_role", ROLE_REPONSE);
					mClientInfomation.put("request_client_nat_address", getRemoteInetAddress());
					mClientInfomation.put("request_client_nat_port", getRemotePort());
					result = "no_need_feedback";
				}
			} catch (Exception e) {
				//Log.PrintError(TAG, "parseInformation getString name Exception = " + e.getMessage());
			}
		}
		return result;
	}
	
	private String parseStatus(JSONObject data) {
		String result = "unknown";
		if (data != null && data.length() > 0) {
			mClientStatus = data.getJSONObject("status");
			try {
				result = "parseStatus_" + mClientStatus.getString("status") + "_ok";
			} catch (Exception e) {
				//Log.PrintError(TAG, "parseStatus getString status Exception = " + e.getMessage());
			}
		}
		return result;
	}
	
	private void delayMs(long ms) {
		try {
			Thread.sleep(ms);
		} catch (Exception e) {
			// TODO: handle exception
			Log.PrintError(TAG, "delayMs = " + e.getMessage());
		}
	}
	
	private void parseClientRole(String mess) {
		if ("unknown".equals(mess)) {
			if (mClientRole == null) {
				mClientRole = ROLE_REQUEST;
			}
		} else {
			if (mClientRole == null) {
				mClientRole = ROLE_REPONSE;
			}
		}
		if (mClientInfomation == null) {
			mClientInfomation = new JSONObject();
			mClientInfomation.put("client_role", mClientRole);
			mClientInfomation.put("name", ROLE_REQUEST);
			mClientInfomation.put("nat_address", getRemoteInetAddress());
			mClientInfomation.put("nat_port", getRemotePort());
			mClientInfomation.put("connected_transfer_server_address", MainDemo.FIXED_HOST_SITE);
			mClientInfomation.put("connected_transfer_server_port", getLocalPort());
		}
		//client first data packet
		if (mTransferClientCallback != null) {
			JSONObject command = new JSONObject();
			command.put("command", "result");
			JSONObject resultJson = new JSONObject();
			resultJson.put("status", "connected_to_transfer_server");
			resultJson.put("information", mClientInfomation);
			command.put("result", resultJson);
			mTransferClientCallback.onTransferClientCommand(TransferClient.this, command);
			sendMessage(command.toString());
		}
	}
	
	private boolean transferBuffer(byte[] buffer, int start, int end) {
		//Log.PrintLog(TAG, "transferBuffer " + mClientInfomation + ", end = " + end);
		boolean result = false;
		try {
			if (mSocketWriter != null) {
				mSocketWriter.write(buffer, start, end);
				mSocketWriter.flush();
				result = true;
			}
		} catch (Exception e) {
			Log.PrintError(TAG, "transferBuffer Exception = " + e.getMessage());
		}
		return result;
	}
	
	private void dealClearWork() {
		Log.PrintLog(TAG, "closeStream isRunning = " + isRunning);
		if (isRunning) {
			closeSocket();
			closeStream();
			isRunning = false;
		} else {
			closeStream();
		}
	}
	
	private void closeStream() {
		Log.PrintLog(TAG, "closeStream");
		closeBufferedWriter();
		closeOutputStream();
		closeBufferedReader();
		closeInputStream();
	}
	
	private void closeInputStream() {
		try {
			if (mInputStream != null) {
				mInputStream.close();
				mInputStream = null;
			}
		} catch (Exception e) {
			Log.PrintError(TAG, "closeInputStream Exception = " + e.getMessage());
			mInputStream = null;
		}
	}
	
	private void closeBufferedReader() {
		try {
			if (mSocketReader != null) {
				mSocketReader.close();
				mSocketReader = null;
			}
		} catch (Exception e) {
			Log.PrintError(TAG, "closeBufferedReader Exception = " + e.getMessage());
			mSocketReader = null;
		}
	}
	
	private void closeOutputStream() {
		try {
			if (mOutputStream != null) {
				mOutputStream.close();
				mOutputStream = null;
			}
		} catch (Exception e) {
			Log.PrintError(TAG, "closeOutputStream Exception = " + e.getMessage());
			mOutputStream = null;
		}
	}
	
	private void closeBufferedWriter() {
		try {
			if (mSocketWriter != null) {
				mSocketWriter.close();
				mSocketWriter = null;
			}
		} catch (Exception e) {
			Log.PrintError(TAG, "closeBufferedWriter Exception = " + e.getMessage());
			mSocketWriter = null;
		}
	}
	
	private void closeSocket() {
		try {
			if (mClientSocket != null) {
				mClientSocket.close();
				mClientSocket = null;
			}
		} catch (Exception e) {
			Log.PrintError(TAG, "closeSocket Exception = " + e.getMessage());
			mClientSocket = null;
		}
	}
}
