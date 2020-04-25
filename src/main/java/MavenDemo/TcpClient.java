package MavenDemo;

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
	private BufferedReader mSocketReader = null;
	private BufferedWriter mSocketWriter = null;
	private JSONObject mClientInfomation = null;//add mac address as name
	private boolean isRunning = false;
	private List<TransferServer> mTransferServers = Collections.synchronizedList(new ArrayList<TransferServer>());
	
	private TransferServerCallback mTransferServerCallback = new TransferServerCallback() {

		@Override
		public void onTransferServerConnect(TransferServer server, JSONObject data) {
			addTransferServer(server);
			if (data != null && data.length() > 0) {
				data.put("action", "server_started");
				sendMessage(data.toString());
			}
		}

		@Override
		public void onTransferServerDisconnect(TransferServer server, JSONObject data) {
			removeTransferServer(server);
			if (data != null && data.length() > 0) {
				data.put("action", "server_stopped");
				sendMessage(data.toString());
			}
		}
	};
	
	private TransferClientCallback mTransferClientCallback = new TransferClientCallback() {

		@Override
		public void onTransferClientCommand(TransferClient client, JSONObject data) {
			// TODO Auto-generated method stub
			Log.PrintLog(TAG, "onTransferClientCommand data = " + data);
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
				mSocketReader = new BufferedReader(new InputStreamReader(mInputStream, Charset.forName("UTF-8")));
				mSocketWriter = new BufferedWriter(new OutputStreamWriter(mOutputStream));
				String inMsg = null;
				String outMsg = null;
				while (isRunning) {
					try {
					    while ((inMsg = mSocketReader.readLine()) != null) {
					    	Log.PrintLog(TAG, "Received from  client: " + inMsg);
					    	outMsg = dealCommand(inMsg);
					    	sendMessage(outMsg);
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
			if (mSocketWriter != null) {
				mSocketWriter.write(outMsg);
		    	//mSocketWriter.write("\n");
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
						result = parseInformation(obj);
						break;
					case "start_new_transfer_server":
						result = parseStartNewServer(obj);
						break;
					case "stop_transfer_server":
						result = parseStartNewServer(obj);
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
			mClientInfomation = data.getJSONObject("information");
			try {
				if (mClientInfomation != null && mClientInfomation.length() > 0) {
					
				}
				result = "parseInformation_" + mClientInfomation.getString("name") + "_" + mClientInfomation.getString("mac_address") + "_ok";
				//add nat address
				mClientInfomation.put("response_client_nat_address", getRemoteInetAddress());
				mClientInfomation.put("response_client_nat_port", getRemotePort());
				if (mClientCallback != null) {
					mClientCallback.onClientConnect(this, mClientInfomation);
				}
			} catch (Exception e) {
				Log.PrintError(TAG, "parseInformation getString name Exception = " + e.getMessage());
			}
		}
		return result;
	}
	
	private String parseStartNewServer(JSONObject data) {
		String result = "unknown";
		JSONObject serverObj = null;
		String address = null;
		int port = -1;
		String bondedReponseAddress = null;
		int bondedReponsePort = -1;
		if (data != null && data.length() > 0) {
			////request a new transfer server
			//{"command":"start_new_transfer_server","server_info":{"new_transfer_server_address":"opendiylib.com","new_transfer_server_port":19909,"bonded_response_server_address":"192.168.188.150","bonded_response_server_port":19911}}
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
						TransferServer transferServer = new TransferServer(address, port, bondedReponseAddress, bondedReponsePort);
						transferServer.setClientCallback(mTransferServerCallback);
						transferServer.setTransferClientCallback(mTransferClientCallback);
						transferServer.startServer();
					} else {
						result = "parseStartNewServer_" + address + ":" + port + "_exist_ok";
						Log.PrintLog(TAG, "parseStartNewServer exist server = " + address + ":" + port);
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
						result = "parseStopTransferServer_" + address + ":" + port + "_ok";
					} else {
						result = "parseStopTransferServer_" + address + ":" + port + "_not_exist_ok";
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
		if (data != null && data.length() > 0) {
			try {
				result = "parseStatus_" + mClientInfomation.getString("status") + "_ok";
			} catch (Exception e) {
				Log.PrintError(TAG, "parseStatus getString status Exception = " + e.getMessage());
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
}
