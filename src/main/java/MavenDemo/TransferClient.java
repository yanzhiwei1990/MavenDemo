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
	//private TransferClient mDestinationClient = null;
	private TransferServer mTransferServer = null;
	private ClientCallback mClientCallback = null;
	private ExecutorService mExecutorService = null;
	private InputStream mInputStream = null;
	private OutputStream mOutputStream = null;
	private BufferedInputStream mSocketReader = null;
	private BufferedOutputStream mSocketWriter = null;
	private JSONObject mClientInfomation = null;//add mac address as name
	private JSONObject mClientStatus = null;//online offline
	private boolean mIsRunning = false;
	private InetAddress mRemoteAddress = null;
	private InetAddress mLocalAddress = null;
	private boolean mRecognised = false;
	private String mClientRole = null;
	private TransferClientCallback mTransferClientCallback = null;
	
	public static final String ROLE_REQUEST = "request";
	public static final String ROLE_REPONSE = "response";
	
	private Runnable mStartListener = new Runnable() {

		public void run() {
			Log.PrintLog(TAG, "startListener running=" + TransferClient.this);
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
				Log.PrintLog(TAG, "receive 0000");
				String inMsg = null;
				String outMsg = null;
				byte[] buffer = new byte[1024 * 1024];
				int length = -1;
				mSocketReader = new BufferedInputStream(mInputStream, buffer.length);
				mSocketWriter = new BufferedOutputStream(mOutputStream, buffer.length);

				while (mIsRunning) {
					Log.PrintLog(TAG, "receive while isRunning");
					try {
					    while (true) {
					    	Log.PrintLog(TAG, "receive while true");
					    	length = mSocketReader.read(buffer, 0, buffer.length);
					    	if (length == -1) {
					    		Log.PrintLog(TAG, "receive length == -1");
					    		break;
					    	}
					    	if (length <= 1024) {
					    		try {
					    			inMsg = new String(buffer, 0, length, Charset.forName("UTF-8")).trim();
								} catch (Exception e) {
									inMsg = null;
									Log.PrintError(TAG, "parse first 256 bytes error");
								}
					    		outMsg = dealCommand(inMsg);
					    		/*if (!"no_need_feedback".equals(outMsg) && !"unknown".equals(outMsg)) {
					    			Log.PrintLog(TAG, "Received dealt outMsg = " + outMsg);
					    			JSONObject result = new JSONObject();
							    	result.put("command", "result");
							    	result.put("result", outMsg);
							    	sendMessage(result.toString());
					    		}*/
					    	} else {
					    		outMsg = "unknown";
					    	}
					    	//Log.PrintLog(TAG, "receive 1111 " + mClientRole + ", length = " + length);
					    	//Log.PrintLog(TAG, "length = " + length + ", inMsg = " + inMsg + ",outMsg = " + outMsg);
					    	if (!mRecognised) {
					    		mRecognised = true;
					    		parseClientRole(outMsg);
					    	}
					    	if ("unknown".equals(outMsg)) {
					    		//Log.PrintLog(TAG, "receive unknown");
					    		//need to transfer
					    		if (ROLE_REPONSE.equals(mClientRole)) {
					    			//Log.PrintLog(TAG, "receive 2222 ROLE_REPONSE");
					    			if (mTransferServer.mRequestTransferClient != null) {
					    				Log.PrintLog(TAG, "Received found ROLE_REQUEST client " + TransferClient.this);
					    				mTransferServer.mRequestTransferClient.transferBuffer(buffer, 0, length);
					    			}  else {
					    				Log.PrintLog(TAG, "Received wait ROLE_REQUEST client " + TransferClient.this);
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
					    		} else if (ROLE_REQUEST.equals(mClientRole)) {
					    			//Log.PrintLog(TAG, "receive 3333 ROLE_REQUEST");
					    			//request client need to wait for respponse client ready
					    			int count = 50;
					    			while (mTransferServer.mResponseTransferClient == null) {
					    				Log.PrintLog(TAG, "Received wait ROLE_REPONSE client " + TransferClient.this);
					    				delayMs(100);
					    				//mDestinationClient = mTransferServer.getTransferClient(ROLE_REPONSE, getRemoteInetAddress(), getRemotePort());
					    				count--;
					    				if (count < 0) {
					    					Log.PrintLog(TAG, "wait response client 30s time out");
					    					break;
					    				}
					    			}
					    			if (count < 0) {
					    				Log.PrintLog(TAG, "stop request client as time out");
					    				break;
					    			} else {
					    				Log.PrintLog(TAG, "response time out count = " + count);
					    			}
					    			if (mTransferServer.mResponseTransferClient != null) {
					    				Log.PrintLog(TAG, "Received found ROLE_REPONSE client " + TransferClient.this);
					    				mTransferServer.mResponseTransferClient.transferBuffer(buffer, 0, length);
					    			} else {
					    				Log.PrintLog(TAG, "Received no found ROLE_REPONSE client" + TransferClient.this);
					    			}
					    		} else {
					    			Log.PrintLog(TAG, "Received unkown role");
					    		}
					    	} else {
					    		Log.PrintLog(TAG, "receive not unkown = " + TransferClient.this);
					    	}
					    }
					    Log.PrintLog(TAG, "startListener disconnect " + TransferClient.this);
					   
					} catch(Exception e) {
						Log.PrintError(TAG, "accept Exception = " + e.getMessage());
						e.printStackTrace();
						break;
					}
					Log.PrintLog(TAG, "receive isRunning break");
					break;
				}
			} else {
				Log.PrintError(TAG, "accept get stream error");
			}
			Log.PrintLog(TAG, "stop accept " + TransferClient.this);
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
		mIsRunning = true;
		mExecutorService.submit(mStartListener);
	}
	
	public void stopListen() {
		Log.PrintLog(TAG, "stopListen");
		mIsRunning = false;
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
	
	public String getNatAddress() {
		String result = null;
		try {
			result = mClientInfomation.getString("nat_address");
		} catch (Exception e) {
			//Log.PrintError(TAG, "getNatAddress Exception = " + e.getMessage());
		}
		return result;
	}
	
	public int getNatPort() {
		int result = -1;
		try {
			result = mClientInfomation.getInt("nat_port");
		} catch (Exception e) {
			//Log.PrintError(TAG, "getNatPort Exception = " + e.getMessage());
		}
		return result;
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
			if (obj != null && obj.length() > 0) {
				try {
					command = obj.getString("command");
				} catch (Exception e) {
					//Log.PrintError(TAG, "dealCommand getString command Exception = " + e.getMessage());
				}
				switch (command) {
					case "information":
						Log.PrintLog(TAG, "dealCommand information");
						result = parseInformation(obj);
						break;
					case "status":
						Log.PrintLog(TAG, "dealCommand status");
						result = parseStatus(obj);
						break;
					default:
						Log.PrintLog(TAG, "dealCommand default");
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
							"name":"response_fixed_request_tranfer_client",
							"mac_address","10-7B-44-15-2D-B6",
							"client_role","request",
							"connected_server_address":"www.opendiylib.com",
							"connected_server_port":19920,
							"dhcp_address","192.168.188.150",
							"dhcp_port":5555,
							"nat_address":"",
							"nat_port":-1
							
							//transfer server
							"connected_transfer_server_address":"www.opendiylib.com",
							"connected_transfer_server_port":19920,
							"request_client_nat_address":"58.246.136.202",
							"request_client_nat_port":50000,
							"bonded_response_server_address","192.168.188.150"
							"bonded_response_server_port":19920
						}
				} 
			*/
			mClientInfomation = data.getJSONObject("information");
			try {
				if (mClientInfomation != null && mClientInfomation.length() > 0) {
					//add nat address
					mClientInfomation.put("nat_address", getRemoteInetAddress());
					mClientInfomation.put("nat_port", getRemotePort());

					result = "client_ready";
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
		} else if ("client_ready".equals(mess)) {
			if (mClientRole == null) {
				mClientRole = ROLE_REPONSE;
			}
		}
		if (mClientInfomation == null) {
			mClientInfomation = new JSONObject();
			mClientInfomation.put("client_role", mClientRole);
			mClientInfomation.put("name", ROLE_REQUEST);
			mClientInfomation.put("mac_address", "unkown");
			mClientInfomation.put("nat_address", getRemoteInetAddress());
			mClientInfomation.put("nat_port", getRemotePort());
			mClientInfomation.put("connected_transfer_server_address", MainDemo.FIXED_HOST_SITE);
			mClientInfomation.put("connected_transfer_server_port", getLocalPort());
		}
		
		if (ROLE_REPONSE.equals(mClientRole)) {
			JSONObject command = new JSONObject();
			command.put("command", "result");
			JSONObject resultJson = new JSONObject();
			resultJson.put("information", mClientInfomation);
			resultJson.put("status", "request_response_connected_to_transfer_server");
			command.put("result", resultJson);
			//send ack result to transferclient
			sendMessage(command.toString());
			//send result to tcpclient
			if (mTransferClientCallback != null) {
				mTransferClientCallback.onTransferClientCommand(TransferClient.this, command);
			}
		} else if (ROLE_REQUEST.equals(mClientRole)) {
			//send result to tcpclient
			if (mTransferClientCallback != null) {
				//tell tcpclient to connect transfer server
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
		} else {
			Log.PrintLog(TAG, "parseClientRole unkown role");
		}
		//add client
		if (mClientCallback != null) {
			mClientCallback.onClientConnect(TransferClient.this, null);
		}
		if (mTransferServer.mResponseTransferClient == null) {
			mTransferServer.mResponseTransferClient = TransferClient.this;
		}
		Log.PrintLog(TAG, "parseClientRole recognise client = " + TransferClient.this);
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
		Log.PrintLog(TAG, "closeStream isRunning = " + mIsRunning);
		if (mIsRunning) {
			closeSocket();
			closeStream();
			mIsRunning = false;
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
	
	@Override
	public String toString() {
		String result = "unkown";
		if (mClientInfomation != null) {
			try {
				result = mClientInfomation.getString("name");
			} catch (Exception e) {
				result = "unkown";
				Log.PrintError(TAG, "toString  getString name Exception = " + e.getMessage());
			}
			try {
				if ("unkown".equals(result)) {
					result = mClientInfomation.getString("mac_address");
				} else {
					result = result + ":" + mClientInfomation.getString("mac_address");
				}
			} catch (Exception e) {
				result = "unkown";
				Log.PrintError(TAG, "toString  getString mac_address Exception = " + e.getMessage());
			}
			if (ROLE_REQUEST.equals(mClientRole)) {
				result = result + "-" + mClientRole + "-" + getRemoteInetAddress() + ":" + getRemotePort() + "->" + getLocalInetAddress() + ":" + getLocalPort();
			} else if (ROLE_REPONSE.equals(mClientRole)) {
				result = result + "-" + mClientRole + "-" + getLocalInetAddress() + ":" + getLocalPort() + "->" + getRemoteInetAddress() + ":" + getRemotePort();
			} else {
				Log.PrintLog(TAG, "TransferClient other role");
			}
			
		}
		return result;
	}
}
