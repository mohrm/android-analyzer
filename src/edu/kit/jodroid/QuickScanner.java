package edu.kit.jodroid;

import java.io.File;
import java.io.IOException;

import org.json.JSONException;

import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;

import edu.kit.jodroid.ifc.CGBasedSrcSnkScanner.ScanResult;
import edu.kit.jodroid.io.AppSpec;
import edu.kit.jodroid.io.ParsePolicyFromJSON.Sink;
import edu.kit.jodroid.io.ParsePolicyFromJSON.Source;


public class QuickScanner {
	public static void main(String[] args) throws JSONException, IOException, CancelException, UnsoundGraphException, WalaException, InterruptedException, APKToolException {
		
		
		if(args.length == 0){
			printUsage();
			System.exit(1);
		}
		File apkFile = new File(args[0]);
		if(!apkFile.exists()) {
			System.err.printf("File %s doesn't exist.%n",args[0]);
			printUsage();
			System.exit(1);			
		}
		
		boolean NO_CG = false;
		String PREFIX = apkFile.getName().substring(0,apkFile.getName().lastIndexOf("."));
		int TIMEOUTP = 600;
		try {
			for(int i = 1; i < args.length; i++) {
				switch(args[i]) {
					case "--no-cg":
						NO_CG = true;
						break;
					case "--timeout":
						TIMEOUTP = Integer.parseInt(args[i+1]);
						i++;
						break;
					case "--prefix":
						PREFIX = args[i+1];
						i++;
						break;
					default:
						System.err.printf("ERROR: Unsupported argument %s.", args[i]);
						printUsage();
						System.exit(1);
				}			
			}
		} catch (Exception e) {
			printUsage();
			e.printStackTrace(System.err);			
			System.exit(1);
		}
		final int TIMEOUT = TIMEOUTP;
		Thread monitor = new Thread() {
			@Override public void run() {
				final long stop = System.currentTimeMillis() + TIMEOUT*1000l;
				while(System.currentTimeMillis() < stop) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						System.err.println("Interrupt in monitor.");
						e.printStackTrace();
					}
				}
				System.err.printf("ERROR: Timeout of %d seconds exceeded. Aborting execution.%n",TIMEOUT);
				System.exit(27);
			}
		};
		monitor.setDaemon(true);
		monitor.start();
		
		File manifestFile = MainAnalysis.extractManifest(apkFile, true);
		
		ScanResult scanResult;
		if(NO_CG)
			scanResult = new AndroidAnalysis().justScanFast(new AppSpec(apkFile, manifestFile));
		else
			scanResult = new AndroidAnalysis().justScan(new AppSpec(apkFile, manifestFile));
		
		
		for (Source src : scanResult.sources) {
			System.out.println(String.format("%s|Source|%s|%s|%s", PREFIX, src.getDeclaringClass()+"."+src.getMethod(), src.getCategory(), src.params2String()));
		}
		for (Sink snk : scanResult.sinks) {
			System.out.println(String.format("%s|Sink|%s|%s|%s", PREFIX, snk.getDeclaringClass()+"."+snk.getMethod(), snk.getCategory(), snk.params2String().replaceAll("\\s", "_")));
		}
	}
	public static void printUsage(){
		System.out.println("Usage:\n\tquickscan <package.apk> [OPTIONS]");
		System.out.println("OPTIONS:");
		System.out.println("\t--no-cg\n\t\tDon't build call-graph, uses only class hierarchy containing dead bundled library code.");
		System.out.println("\t--prefix <prefix>\n\t\tPrint '<prefix>|' in front of each output line. Defaults to <package>.");
		System.out.println("\t--timeout <timeout>\n\t\tAbort analysis after <timeout> seconds. Defaults to 600.");		
	}
}