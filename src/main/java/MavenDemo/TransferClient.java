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
import java.util.concurrent.ExecutorService;

import org.json.JSONObject;

import MavenDemo.TransferServer.ClientCallback;

public class TransferClient {

	public static final String TAG = TransferClient.class.getSimpleName() + " : %s\n";
	
	private Socket mClientSocket = null;
	private ClientCallback mClientCallback = null;
	private ExecutorService mExecutorService = null;
	private InputStream mInputStream = null;
	private OutputStream mOutputStream = null;
	private BufferedInputStream mSocketReader = null;
	private BufferedOutputStream mSocketWriter = null;
	private JSONObject mClientInfomation = null;//add mac address as name
	private JSONObject mClientStatus = null;//online offline
	private boolean isRunning = false;
	
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
					    	if (length <= 100) {
					    		try {
					    			inMsg = new String(buffer, 0, length, Charset.forName("UTF-8")).trim();
								} catch (Exception e) {
									inMsg = null;
									Log.PrintError(TAG, "parse first 100 bytes error");
								}
					    		Log.PrintLog(TAG, "Received from  client: " + inMsg);
					    		outMsg = dealCommand(inMsg);
					    		if (!"unknown".equals(outMsg)) {
					    			sendMessage(outMsg);
					    		}
					    	} else {
					    		outMsg = "unknown";
					    	}
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
	
	public TransferClient(ExecutorService executor, Socket socket) {
		mClientSocket = socket;
		mExecutorService = executor;
	}

	public void setClientCallback(ClientCallback callback) {
		mClientCallback = callback;
	}
	
	public void startListen() {
		Log.PrintLog(TAG, "startListen");
		isRunning = true;
		mExecutorService.submit(mStartListener);
		if (mClientCallback != null) {
			mClientCallback.onClientConnect(this, null);
		}
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
	
	private void sendMessage(String outMsg) {
		try {
			if (mSocketWriter != null && outMsg != null && outMsg.length() > 0) {
				byte[] send = (outMsg + "\n").getBytes(Charset.forName("UTF-8"));
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
				result = "parseInformation_" + mClientInfomation.getString("name") + "_ok";
				if (mClientCallback != null) {
					mClientCallback.onClientConnect(TransferClient.this, mClientInfomation);
				}
			} catch (Exception e) {
				Log.PrintError(TAG, "parseInformation getString name Exception = " + e.getMessage());
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
				Log.PrintError(TAG, "parseStatus getString status Exception = " + e.getMessage());
			}
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
