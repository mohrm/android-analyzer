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
		File apkFile = new File(args[0]);
		File manifestFile = MainAnalysis.extractManifest(apkFile, true);
		ScanResult scanResult = new AndroidAnalysis().justScanFast(new AppSpec(apkFile, manifestFile));
		for (Source src : scanResult.sources) {
			System.out.println(String.format("%s|Source|%s|%s|%s", args[1], src.getDeclaringClass()+"."+src.getMethod(), src.getCategory(), src.params2String()));
		}
		for (Sink snk : scanResult.sinks) {
			System.out.println(String.format("%s|Sink|%s|%s|%s", args[1], snk.getDeclaringClass()+"."+snk.getMethod(), snk.getCategory(), snk.params2String().replaceAll("\\s", "_")));
		}
	}
}
