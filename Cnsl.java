/**
 * A useful logging class that enables/disables
 * all output to the console.
 * 
 * @author shaon
 *
 */

public class Cnsl {
	public static final boolean ENABLE_LOG = false;
	public static void println(String s) {
		if (ENABLE_LOG) {
			System.out.println(s);
		}
	}
	
	public static void print(String s) {
		if (ENABLE_LOG) {
			System.out.print(s);
		}
	}

}
