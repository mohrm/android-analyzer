package edu.kit.jodroid;

import java.io.File;
import java.io.IOException;

import org.json.JSONException;

import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;

import edu.kit.jodroid.ifc.SrcSnkScanner.ScanResult;
import edu.kit.jodroid.io.AppSpec;


public class QuickScanner {
	public static void main(String[] args) throws JSONException, IOException, CancelException, UnsoundGraphException, WalaException, InterruptedException, APKToolException {
		File apkFile = new File(args[0]);
		File manifestFile = MainAnalysis.extractManifest(apkFile);
		ScanResult scanResult = new AndroidAnalysis().justScan(new AppSpec(apkFile, manifestFile));
		System.out.println();
		scanResult.print(System.out);
	}
}
