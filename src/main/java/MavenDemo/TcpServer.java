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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

public class TcpServer {
	
	private static final String TAG = TcpServer.class.getSimpleName() + " : %s\n";
	private static final boolean DEBUG  = Debug.TCPSERVER;
	private static final int MAX_THREAD = 10;
	private ServerSocket mServerSocket = null;
	private String mTcpAddress = null;
	private int mTcpPort = -1;
	private ExecutorService mExecutorService = null;
	private boolean isServerRuning = false;
	private List<TcpClient> mTcpClients = Collections.synchronizedList(new ArrayList<TcpClient>());
	private ClientCallback mClientCallback = new ClientCallback() {
		
		public void onClientDisconnect(TcpClient client, JSONObject data) {
			Log.PrintLog(TAG, "startSeonClientDisconnectrver client " + client);
			removeTcpClient(client);
		}
		
		public void onClientConnect(TcpClient client, JSONObject data) {
			Log.PrintLog(TAG, "onClientConnect client " + client);
			addTcpClient(client);
		}
	};
	
	private Runnable mStartServer = new Runnable() {

		public void run() {
			Log.PrintLog(TAG, "startServer start accept");
			while (isServerRuning) {
				TcpClient tcpClient = null;
				try {
					tcpClient = new TcpClient(mExecutorService, mServerSocket.accept());
					tcpClient.setClientCallback(mClientCallback);
					tcpClient.startListen();
				} catch (IOException e) {
					Log.PrintError(TAG, "startServer accept Exception = " + e.getMessage());
				}
			}
			Log.PrintLog(TAG, "startServer stop accept");
			dealClearWork();
		}
	};
	
	public TcpServer(String address, int port) {
		mTcpAddress = address;
		mTcpPort = port;
		mExecutorService = Executors.newFixedThreadPool(MAX_THREAD);
	}
	
	public  void startServer() {
		if (mTcpAddress != null && mTcpAddress.length() > 0 && mTcpPort > 0) {
			Log.PrintLog(TAG, "startServer");
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
	
	private void addTcpClient(TcpClient client) {
		//Log.PrintLog(TAG, "addTcpClient = " + client.getClientInformation());
		mTcpClients.add(client);
	}
	
	private void removeTcpClient(TcpClient client) {
		//Log.PrintLog(TAG, "removeTcpClient = " + client.getClientInformation());
		mTcpClients.remove(client);
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
		Iterator<TcpClient> iterator = mTcpClients.iterator();
		while (iterator.hasNext()) {
			TcpClient client = (TcpClient)iterator.next();
			client.stopListen();
			client = null;
		}
		mTcpClients.clear();
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
		void onClientConnect(TcpClient client, JSONObject data);
		void onClientDisconnect(TcpClient client, JSONObject data);
	}
	
	@Override
	public String toString() {
		String result = "unkown";
		if (mTcpAddress != null) {
			result = mTcpAddress + ":" + mTcpPort;
		}
		return result;
	}
}
