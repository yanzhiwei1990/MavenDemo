package MavenDemo;

public class MainDemo {

	private TcpServer mTcpServer = null;
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		MainDemo mMainDemo = new MainDemo();
		mMainDemo.startRun();
	}
	
	private void startRun() {
		initShutDownWork();
		mTcpServer = new TcpServer("127.0.0.1", 19900);
		mTcpServer.startServer();
		//TcpClient.startConnect("127.0.0.1", 19901);
	}
	
	private void stopRun() {
		mTcpServer.stopServer();
		//TcpClient.stopConnect();
	}
	
	private void print(String format, Object... args) {
		System.out.printf(format, args);
	}
	
	private void initShutDownWork() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
		    public void run() {
		    	print("doShutDownWork\n");
		    	stopRun();
		    }  
		});
	}
}
