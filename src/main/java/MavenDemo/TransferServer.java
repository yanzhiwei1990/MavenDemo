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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

public class TransferServer {
	
	private static final String TAG = TransferServer.class.getSimpleName() + " : %s\n";
	private static final boolean DEBUG  = Debug.TCPSERVER;
	private static final int MAX_THREAD = 10;
	private ServerSocket mServerSocket = null;
	private String mTcpAddress = null;
	private int mTcpPort = -1;
	private ExecutorService mExecutorService = null;
	private boolean isServerRuning = false;
	private List<TransferClient> mTransferClients = new ArrayList<TransferClient>();
	private ClientCallback mClientCallback = new ClientCallback() {
		
		public void onClientDisconnect(TransferClient client, JSONObject data) {
			// TODO Auto-generated method stub
			addTransferClient(client);
		}
		
		public void onClientConnect(TransferClient client, JSONObject data) {
			// TODO Auto-generated method stub
			removeTransferClient(client);
		}
	};
	
	private Runnable mStartServer = new Runnable() {

		public void run() {
			Log.PrintLog(TAG, "startServer start accept");
			while (isServerRuning) {
				TransferClient transferClient = null;
				try {
					transferClient = new TransferClient(mExecutorService, mServerSocket.accept());
					transferClient.setClientCallback(mClientCallback);
					transferClient.startListen();
				} catch (IOException e) {
					Log.PrintError(TAG, "startServer accept Exception = " + e.getMessage());
				}
			}
			Log.PrintLog(TAG, "startServer stop accept");
			dealClearWork();
		}
	};
	
	public TransferServer(String address, int port) {
		mTcpAddress = address;
		mTcpPort = port;
		mExecutorService = Executors.newFixedThreadPool(MAX_THREAD);
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
	
	private void addTransferClient(TransferClient client) {
		mTransferClients.add(client);
	}
	
	private void removeTransferClient(TransferClient client) {
		mTransferClients.remove(client);
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