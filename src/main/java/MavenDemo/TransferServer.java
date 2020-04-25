package MavenDemo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

import MavenDemo.TcpClient.TransferClientCallback;
import MavenDemo.TcpClient.TransferServerCallback;
import io.netty.channel.unix.Buffer;

public class TransferServer {
	
	private static final String TAG = TransferServer.class.getSimpleName() + " : %s\n";
	private static final boolean DEBUG  = Debug.TCPSERVER;
	private static final int MAX_THREAD = 10;
	private ServerSocket mServerSocket = null;
	private String mTcpAddress = null;
	private int mTcpPort = -1;
	private String mBondedResponseAddress = null;
	private int mBondedResponsePort = -1;
	private ExecutorService mExecutorService = null;
	private boolean isServerRuning = false;
	private List<TransferClient> mTransferClients = /*new ArrayList<TransferClient>();*/Collections.synchronizedList(new ArrayList<TransferClient>());
	private TransferServerCallback mTransferServerCallback = null;
	private TransferClientCallback mTransferClientCallback = null;
	private ClientCallback mClientCallback = new ClientCallback() {
		
		public void onClientDisconnect(TransferClient client, JSONObject data) {
			// TODO Auto-generated method stub
			removeTransferClient(client);
		}
		
		public void onClientConnect(TransferClient client, JSONObject data) {
			// TODO Auto-generated method stub
			addTransferClient(client);
		}
	};
	
	private Runnable mStartServer = new Runnable() {

		public void run() {
			Log.PrintLog(TAG, "startServer start accept");
			while (isServerRuning) {
				TransferClient transferClient = null;
				try {
					if (mTransferServerCallback != null) {
						mTransferServerCallback.onTransferServerConnect(TransferServer.this, null);
					}
					transferClient = new TransferClient(mExecutorService, TransferServer.this, mServerSocket.accept());
					transferClient.setClientCallback(mClientCallback);
					transferClient.setTransferClientCallback(mTransferClientCallback);
					transferClient.startListen();
				} catch (IOException e) {
					Log.PrintError(TAG, "startServer accept Exception = " + e.getMessage());
				}
			}
			Log.PrintLog(TAG, "startServer stop accept");
			dealClearWork();
			if (mTransferServerCallback != null) {
				mTransferServerCallback.onTransferServerDisconnect(TransferServer.this, null);
			}
		}
	};
	
	public TransferServer(String address, int port, String bondedAddress, int bondedPort) {
		mTcpAddress = address;
		mTcpPort = port;
		mBondedResponseAddress = bondedAddress;
		mBondedResponsePort = bondedPort;
		mExecutorService = Executors.newFixedThreadPool(MAX_THREAD);
	}
	
	public void setClientCallback(TransferServerCallback callback) {
		mTransferServerCallback = callback;
	}
	
	public void setTransferClientCallback(TransferClientCallback callback) {
		mTransferClientCallback = callback;
	}

	public  void startServer() {
		if (mTcpAddress != null && mTcpAddress.length() > 0 && mTcpPort > 0) {
			if (mServerSocket != null) {
				try {
					mServerSocket.close();
					mServerSocket = null;
				} catch (IOException e) {
					mServerSocket = null;
					Log.PrintError(TAG, "startServer not null but close Exception = " + e.getMessage());
				}
			}
			try {
				mServerSocket = new ServerSocket();
				mServerSocket.setReuseAddress(true);
			    mServerSocket.bind(new InetSocketAddress(mTcpAddress, mTcpPort), MAX_THREAD);
			} catch (IOException e) {
				mServerSocket = null;
				Log.PrintError(TAG, "startServer bind Exception = " + e.getMessage());
				return;
			}
			isServerRuning = true;
			mExecutorService.submit(mStartServer);
		} else {
			Log.PrintLog(TAG, "startServer error no address");
		}
	}
	
	public void stopServer() {
		Log.PrintLog(TAG, "stopServer");
		isServerRuning = false;
		dealClearWork();
	}
	
	public String getAddress() {
		return mTcpAddress;
	}
	
	public int getPort() {
		return mTcpPort;
	}
	
	public String getBondedReponseAddress() {
		return mBondedResponseAddress;
	}
	
	public int getBondedReponsePort() {
		return mBondedResponsePort;
	}
	
	private void addTransferClient(TransferClient client) {
		Log.PrintLog(TAG, "addTransferClient info = " + client.getClientInformation());
		mTransferClients.add(client);
	}
	
	private void removeTransferClient(TransferClient client) {
		Log.PrintLog(TAG, "removeTransferClient = " + client.getClientInformation());
		mTransferClients.remove(client);
	}
	
	public TransferClient getTransferClient(String type, String address, int port) {
		TransferClient result = null;
		if (mTransferClients != null && mTransferClients.size() > 0 && address != null && port != -1) {
			Iterator<TransferClient> iterator = mTransferClients.iterator();
			TransferClient singleClient = null;
			String singlerole = null;
			String singleaddress = null;
			int singleport = -1;
			String singlerequestdddress = null;
			int singlerequestport = -1;
			while (iterator.hasNext()) {
				singleClient = (TransferClient)iterator.next();
				singlerole = singleClient.getClientRole();
				switch (type) {
					case "request":
						singleaddress = singleClient.getRemoteInetAddress();
						singleport = singleClient.getRemotePort();
						if ("request".equals(singlerole) && singleaddress != null && singleaddress.equals(address) &&
								singleport != -1 && singleport == port) {
							result = singleClient;
							break;
						}
						break;
					case "response":
						singlerequestdddress = singleClient.getRequestClientInetAddress();
						singlerequestport = singleClient.getRequestClientPort();
						if ("response".equals(singlerole) && singlerequestdddress != null && singlerequestdddress.equals(address) &&
										singlerequestport != -1 && singlerequestport == port) {
							result = singleClient;
							break;
						}
						break;
				}
			}
		} else {
			Log.PrintLog(TAG, "getTransferClient empty TransferClientList");
		}
		return result;
	} 
	
	private void dealClearWork() {
		Log.PrintLog(TAG, "closeStream isRunning = " + isServerRuning);
		closeAllClient();
		closeServerSocket();
		mExecutorService.shutdown();
		isServerRuning = false;
	}
	
	private void closeAllClient() {
		Log.PrintLog(TAG, "closeAllClient");
		Iterator<TransferClient> iterator = mTransferClients.iterator();
		while (iterator.hasNext()) {
			TransferClient client = (TransferClient)iterator.next();
			client.stopListen();
			client = null;
		}
		mTransferClients.clear();
	}
	
	private void closeServerSocket() {
		Log.PrintLog(TAG, "closeServerSocket");
		try {
			if (mServerSocket != null) {
				mServerSocket.close();
				mServerSocket = null;
			}
		} catch (Exception e) {
			Log.PrintError(TAG, "closeServerSocket Exception = " + e.getMessage());
			mServerSocket = null;
		}
	}
	
	public interface ClientCallback {
		void onClientConnect(TransferClient client, JSONObject data);
		void onClientDisconnect(TransferClient client, JSONObject data);
	}
}
