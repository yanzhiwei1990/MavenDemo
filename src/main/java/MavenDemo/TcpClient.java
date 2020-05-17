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
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.json.JSONObject;

import MavenDemo.TcpServer.ClientCallback;

public class TcpClient {

	public static final String TAG = TcpClient.class.getSimpleName() + " : %s\n";
	
	private Socket mClientSocket = null;
	private ClientCallback mClientCallback = null;
	private ExecutorService mExecutorService = null;
	private InputStream mInputStream = null;
	private OutputStream mOutputStream = null;
	private BufferedInputStream mSocketReader = null;
	private BufferedOutputStream mSocketWriter = null;
	private JSONObject mClientInfomation = null;//add mac address as name
	private boolean isRunning = false;
	private List<TransferServer> mTransferServers = Collections.synchronizedList(new ArrayList<TransferServer>());
	
	private TransferServerCallback mTransferServerCallback = new TransferServerCallback() {

		@Override
		public void onTransferServerConnect(TransferServer server, JSONObject data) {
			Log.PrintLog(TAG, "onTransferClientCommand server = " + server);
			addTransferServer(server);
			/*if (data != null && data.length() > 0) {
				sendMessage(data.toString());
			}*/
		}

		@Override
		public void onTransferServerDisconnect(TransferServer server, JSONObject data) {
			Log.PrintLog(TAG, "onTransferServerDisconnect server = " + server);
			removeTransferServer(server);
			/*if (data != null && data.length() > 0) {
				sendMessage(data.toString());
			}*/
		}
	};
	
	private TransferClientCallback mTransferClientCallback = new TransferClientCallback() {

		@Override
		public void onTransferClientCommand(TransferClient client, JSONObject data) {
			// TODO Auto-generated method stub
			Log.PrintLog(TAG, "onTransferClientCommand client = " + client);
			if (client != null) {
				if (data != null && data.length() > 0) {
					sendMessage(data.toString());
				}
			}
		}
		
	};
	
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
				byte[] buffer = new byte[1024 * 1024];
				int length = -1;
				mSocketReader = new BufferedInputStream(mInputStream, buffer.length);
				mSocketWriter = new BufferedOutputStream(mOutputStream, buffer.length);
				String inMsg = null;
				String outMsg = null;
				JSONObject result = null;
				while (isRunning) {
					try {
				    	while ((length = mSocketReader.read(buffer, 0, buffer.length)) != -1) {
				    		try {
				    			inMsg = new String(buffer, 0, length, Charset.forName("UTF-8")).trim();
							} catch (Exception e) {
								Log.PrintError(TAG, "receive Exception " + e.getMessage());
								inMsg = null;
							}
				    		Log.PrintLog(TAG, "receive inMsg=" + inMsg);
					    	outMsg = dealCommand(inMsg);
					    	/*if (!"no_need_feedback".equals(outMsg) && !"unknown".equals(outMsg)) {
						    	sendMessage(outMsg);
					    	}*/
					    	Log.PrintLog(TAG, "receive outMsg=" + outMsg);
					    }
					    Log.PrintLog(TAG, "startListener disconnect");
					   
					} catch(Exception e) {
						Log.PrintError(TAG, "accept Exception = " + e.getMessage());
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
	
	public TcpClient(ExecutorService executor, Socket socket) {
		mClientSocket = socket;
		mExecutorService = executor;
		printAddress();
	}

	public void setClientCallback(ClientCallback callback) {
		mClientCallback = callback;
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
	
	private void stopStartedTransferServer() {
		Log.PrintLog(TAG, "closeAllClient");
		Iterator<TransferServer> iterator = mTransferServers.iterator();
		while (iterator.hasNext()) {
			TransferServer server = (TransferServer)iterator.next();
			server.stopServer();
			server = null;
		}
		mTransferServers.clear();
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
		return mClientSocket.getInetAddress().getHostAddress();
	}
	
	public int getRemotePort() {
		return mClientSocket.getPort();
	}
	
	public String getLocalInetAddress() {
		return mClientSocket.getLocalAddress().getHostAddress();
	}
	
	public int getLocalPort() {
		return mClientSocket.getLocalPort();
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
	
	private void printAddress() {
		if (mClientSocket != null) {
			Log.PrintLog(TAG, "server:" + getLocalInetAddress() + ":" + getLocalPort() +
					", client:" + getRemoteInetAddress() + ":" + getRemotePort());
		}
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
		stopStartedTransferServer();
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
	
	private void addTransferServer(TransferServer server) {
		mTransferServers.add(server);
	}
	
	private void removeTransferServer(TransferServer server) {
		mTransferServers.remove(server);
	}
	
	/*
	 * response client request creat new transfer server
	*/
	private String dealCommand(String data) {
		String result = "unknown";
		String command = null;
		JSONObject obj = null;
		if (data != null) {
			Log.PrintLog(TAG, "dealCommand " + data);
			try {
				obj = new JSONObject(data);
			} catch (Exception e) {
				Log.PrintError(TAG, "dealCommand new JSONObject Exception = " + e.getMessage());
			}
			if (obj != null && obj.length() > 0) {
				try {
					command = obj.getString("command");
				} catch (Exception e) {
					Log.PrintError(TAG, "dealCommand getString command Exception = " + e.getMessage());
				}
				switch (command) {
					case "information":
						Log.PrintLog(TAG, "dealCommand information");
						result = parseInformation(obj);
						break;
					case "start_new_transfer_server":
						Log.PrintLog(TAG, "dealCommand start_new_transfer_server");
						result = parseStartNewTransferServer(obj);
						break;
					case "stop_transfer_server":
						Log.PrintLog(TAG, "dealCommand stop_transfer_server");
						result = parseStopTransferServer(obj);
						break;
					case "status":
						Log.PrintLog(TAG, "dealCommand status");
						result = parseStatus(obj);
						break;
					case "result":
						Log.PrintLog(TAG, "dealCommand result");
						result = parseResult(obj);
						break;
					default:
						Log.PrintLog(TAG, "dealCommand default");
						break;
				}
			}
		} else {
			Log.PrintLog(TAG, "dealCommand null data");
		}
		return result;
	}
	
	private String parseInformation(JSONObject data) {
		String result = "unknown";
		if (data != null && data.length() > 0) {
			try {
				/*
				{
					"command":"result",
					"result":
						{
							"status":"connected_to_fixed_server",
							"information":
								{
									"name":"response_fixed_request_tranfer_client",
									"mac_address","10-7B-44-15-2D-B6",
									"dhcp_address","192.168.188.150",
									"dhcp_port":5555,
									"fixed_server_address":"opendiylib.com",
									"fixed_server_port":19910,
									"response_fixed_client_nat_address":"58.246.136.202",
									"response_fixed_client_nat_port":50000
								}
						}
					}
						
				}
				*/
				mClientInfomation = data.getJSONObject("information");
				if (mClientInfomation != null && mClientInfomation.length() > 0) {
					//add nat address
					mClientInfomation.put("response_fixed_client_nat_address", getRemoteInetAddress());
					mClientInfomation.put("response_fixed_client_nat_port", getRemotePort());
					JSONObject command = new JSONObject();
					command.put("command", "result");
					JSONObject resultJson = new JSONObject();
					resultJson.put("status", "connected_to_fixed_server");
					resultJson.put("information", mClientInfomation);
					command.put("result", resultJson);
					if (mClientCallback != null) {
						mClientCallback.onClientConnect(this, null);
					}
					sendMessage(command.toString());
					result = "no_need_feedback";
				}
			} catch (Exception e) {
				Log.PrintError(TAG, "parseInformation getString name Exception = " + e.getMessage());
			}
		}
		return result;
	}
	
	private String parseStartNewTransferServer(JSONObject data) {
		String result = "unknown";
		JSONObject serverObj = null;
		String address = null;
		int port = -1;
		String bondedReponseAddress = null;
		int bondedReponsePort = -1;
		if (data != null && data.length() > 0) {
			////request a new transfer server
			/*
			{
				"command":"start_new_transfer_server",
				"server_info":
					{
						"new_transfer_server_address":"0.0.0.0",
						"new_transfer_server_port":19920,
						"bonded_response_server_address","192.168.188.150"
						"bonded_response_server_port":19920
					}
			}
			*/
			try {
				serverObj = data.getJSONObject("server_info");
			} catch (Exception e) {
				Log.PrintError(TAG, "parseStartNewServer getString server_info Exception = " + e.getMessage());
			}
			if (serverObj != null && serverObj.length() > 0) {
				try {
					address = serverObj.getString("new_transfer_server_address");
				} catch (Exception e) {
					Log.PrintError(TAG, "parseStartNewServer getString new_transfer_server_address Exception = " + e.getMessage());
				}
				try {
					port = serverObj.getInt("new_transfer_server_port");
				} catch (Exception e) {
					Log.PrintError(TAG, "parseStartNewServer getString new_transfer_server_port Exception = " + e.getMessage());
				}
				try {
					bondedReponseAddress = serverObj.getString("bonded_response_server_address");
				} catch (Exception e) {
					Log.PrintError(TAG, "parseStartNewServer getString bonded_response_server_address Exception = " + e.getMessage());
				}
				try {
					bondedReponsePort = serverObj.getInt("bonded_response_server_port");
				} catch (Exception e) {
					Log.PrintError(TAG, "parseStartNewServer getInt bonded_response_server_port Exception = " + e.getMessage());
				}
				if (address != null && address.length() > 0 && port != -1 && bondedReponseAddress != null && bondedReponseAddress.length() > 0 && bondedReponsePort != -1) {
					if (!isTransferServerExist(address, port)) {
						result = "parseStartNewServer_" + address + ":" + port + "_ok";
						TransferServer transferServer = new TransferServer(mExecutorService, address, port, bondedReponseAddress, bondedReponsePort);
						transferServer.setClientCallback(mTransferServerCallback);
						transferServer.setTransferClientCallback(mTransferClientCallback);
						transferServer.startServer();
						/*
						{
							"command":"result",
							"result":
								{
									"status":"new_transfer_server_started",
									"server_info":
										{
											"new_transfer_server_address":"0.0.0.0",
											"new_transfer_server_port":19920,
											"bonded_response_server_address","192.168.188.150"
											"bonded_response_server_port":19920
										}
								}
							}	
						}
						*/
						JSONObject command = new JSONObject();
						command.put("command", "result");
						JSONObject resultJson = new JSONObject();
						resultJson.put("status", "new_transfer_server_started");
						resultJson.put("server_info", serverObj);
						command.put("result", resultJson);
						if (mTransferClientCallback != null) {
							mTransferClientCallback.onTransferClientCommand(null, command);
						}
						//result = command.toString();
						result = "no_need_feedback";
					} else {
						/*
						{
							"command":"result",
							"result":
								{
									"status":"transfer_server_existed",
									"server_info":
										{
											"new_transfer_server_address":"0.0.0.0",
											"new_transfer_server_port":19920,
											"bonded_response_server_address","192.168.188.150"
											"bonded_response_server_port":19920
										}
								}
							}
								
						}*/
						JSONObject command = new JSONObject();
						command.put("command", "result");
						JSONObject resultJson = new JSONObject();
						resultJson.put("status", "transfer_server_existed");
						resultJson.put("server_info", serverObj);
						command.put("result", resultJson);
						if (mTransferClientCallback != null) {
							mTransferClientCallback.onTransferClientCommand(null, command);
						}
						//result = command.toString();
						result = "no_need_feedback";
						Log.PrintLog(TAG, "parseStartNewServer exist server = " + serverObj);
					}
				}
			}
		}
		return result;
	}
	
	private String parseStopTransferServer(JSONObject data) {
		String result = "unknown";
		JSONObject serverObj = null;
		String address = null;
		int port = -1;
		String bondedReponseAddress = null;
		int bondedReponsePort = -1;
		if (data != null && data.length() > 0) {
			////request a new transfer server
			//{"command":"stop_transfer_server","server_info":{"new_transfer_server_address":"opendiylib.com","new_transfer_server_port":19909,"bonded_response_server_address":"192.168.188.150","bonded_response_server_port":19911}}
			try {
				serverObj = data.getJSONObject("server_info");
			} catch (Exception e) {
				Log.PrintError(TAG, "parseStopTransferServer getString server_info Exception = " + e.getMessage());
			}
			if (serverObj != null && serverObj.length() > 0) {
				try {
					address = serverObj.getString("new_transfer_server_address");
				} catch (Exception e) {
					Log.PrintError(TAG, "parseStopTransferServer getString new_transfer_server_address Exception = " + e.getMessage());
				}
				try {
					port = serverObj.getInt("new_transfer_server_port");
				} catch (Exception e) {
					Log.PrintError(TAG, "parseStopTransferServer getString new_transfer_server_port Exception = " + e.getMessage());
				}
				try {
					bondedReponseAddress = serverObj.getString("bonded_response_server_address");
				} catch (Exception e) {
					Log.PrintError(TAG, "parseStopTransferServer getString bonded_response_server_address Exception = " + e.getMessage());
				}
				try {
					bondedReponsePort = serverObj.getInt("bonded_response_server_port");
				} catch (Exception e) {
					Log.PrintError(TAG, "parseStopTransferServer getInt bonded_response_server_port Exception = " + e.getMessage());
				}
				if (address != null && address.length() > 0 && port != -1 && bondedReponseAddress != null && bondedReponseAddress.length() > 0 && bondedReponsePort != -1) {
					TransferServer transferServer = getTransferServerExist(address, port);
					if (transferServer != null) {
						transferServer.stopServer();
						JSONObject command = new JSONObject();
						command.put("command", "result");
						JSONObject resultJson = new JSONObject();
						resultJson.put("status", "transfer_server_stopped");
						resultJson.put("server_info", serverObj);
						command.put("result", resultJson);
						result = command.toString();
					} else {
						JSONObject command = new JSONObject();
						command.put("command", "result");
						JSONObject resultJson = new JSONObject();
						resultJson.put("status", "transfer_server_not_found");
						resultJson.put("server_info", serverObj);
						command.put("result", resultJson);
						result = command.toString();
						Log.PrintLog(TAG, "parseStopTransferServer not found server = " + serverObj);
					}
				}
			}
		}
		return result;
	}
	
	private boolean isTransferServerExist(String address, int port) {
		boolean result = false;
		Iterator<TransferServer> iterator = mTransferServers.iterator();
		TransferServer transferServer = null;
		while (iterator.hasNext()) {
			transferServer = (TransferServer)iterator.next();
			if (transferServer.getAddress().equals(address) && transferServer.getPort() == port) {
				result = true;
				break;
			}
		}
		return result;
	}
	
	private TransferServer getTransferServerExist(String address, int port) {
		TransferServer result = null;
		Iterator<TransferServer> iterator = mTransferServers.iterator();
		TransferServer transferServer = null;
		while (iterator.hasNext()) {
			transferServer = (TransferServer)iterator.next();
			if (transferServer.getAddress().equals(address) && transferServer.getPort() == port) {
				result = transferServer;
				break;
			}
		}
		return result;
	}
	
	private String parseStatus(JSONObject data) {
		String result = "unknown";
		JSONObject status = null;
		if (data != null && data.length() > 0) {
			try {
				status = data.getJSONObject("status");
			} catch (Exception e) {
				Log.PrintError(TAG, "parseStatus getString status Exception = " + e.getMessage());
				return result;
			}
			try {
				result = "no_need_feedback";
				Log.PrintLog(TAG, "parseStatus " + status);
			} catch (Exception e) {
				Log.PrintError(TAG, "parseStatus deal status Exception = " + e.getMessage());
			}
		}
		return result;
	}
	
	private String parseResult(JSONObject data) {
		String result = "unknown";
		JSONObject resultJson = null;
		if (data != null && data.length() > 0) {
			try {
				resultJson = data.getJSONObject("result");
			} catch (Exception e) {
				Log.PrintError(TAG, "parseResult getString result Exception = " + e.getMessage());
				return result;
			}
			try {
				result = "no_need_feedback";
				Log.PrintLog(TAG, "parseResult " + resultJson);
			} catch (Exception e) {
				Log.PrintError(TAG, "parseResult deal result Exception = " + e.getMessage());
			}
		}
		return result;
	}
	
	public interface TransferServerCallback {
		void onTransferServerConnect(TransferServer server, JSONObject data);
		void onTransferServerDisconnect(TransferServer server, JSONObject data);
	}
	
	public interface TransferClientCallback {
		void onTransferClientCommand(TransferClient client, JSONObject data);
	}
	
	@Override
	public String toString() {
		String result = "unkown";
		if (mClientInfomation != null) {
			try {
				result = mClientInfomation.getString("name");
			} catch (Exception e) {
				result = "unkown";
				Log.PrintError(TAG, "toString getString name Exception = " + e.getMessage());
			}
			try {
				if ("unkown".equals(result)) {
					result = mClientInfomation.getString("mac_address");
				} else {
					result = result + ":" + mClientInfomation.getString("mac_address");
				}
			} catch (Exception e) {
				result = "unkown";
				Log.PrintError(TAG, "toString getString mac_address Exception = " + e.getMessage());
			}
		}
		return result;
	}
}
