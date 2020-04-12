package MavenDemo;

public class Log {

	//private static final String TAG = Log.class.getSimpleName();
	private static final boolean DEBUG_LOG = true || Debug.LOG;
	private static final boolean DEBUG_ERROR = true || Debug.LOG;
	
	public static void PrintLog(String format, Object... args) {
		if (DEBUG_LOG) System.out.print(String.format(format, args));
	}
	
	public static void PrintError(String format, Object... args) {
		if (DEBUG_ERROR) System.out.print(String.format(format, args));
	}
}