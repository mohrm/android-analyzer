package edu.kit.jodroid;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brut.common.BrutException;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;

import edu.kit.jodroid.ifc.AndroidIFCAnalysis;
import edu.kit.jodroid.ifc.IFCReport;
import edu.kit.jodroid.io.AppSpec;

public class MainAnalysis {
	
	
	
	public static void main(String[] args) throws IOException, ClassHierarchyException, CancelException, UnsoundGraphException, InterruptedException, BrutException {
		runAnalysisOn(listApps("examples/apps"));
		// runAnalysisOn("/home/mmohr-local/workspaces/ws-android/Simple1/Simple1.apk");
	}

	static void runAnalysisOn(Iterable<AppSpec> appSpecs) {
		Map<AppSpec, Boolean> success = new HashMap<AppSpec, Boolean>();
		Map<AppSpec, Throwable> failureReason = new HashMap<AppSpec, Throwable>();
		AndroidAnalysis a = new AndroidAnalysis();
		for (AppSpec appSpec : appSpecs) {
			try {
				System.out.print("Test on " + appSpec + "...");
				AndroidIFCAnalysis ifcAnalysis = a.runAnalysis(appSpec);
				IFCReport report = ifcAnalysis.check();
				report.print(System.out);
				System.out.println("SUCCESS");
				success.put(appSpec, true);
			} catch (Throwable t) {
				System.out.println("FAILURE");
				success.put(appSpec, false);
				failureReason.put(appSpec, t);
			}
		}
		for (Map.Entry<AppSpec, Boolean> e : success.entrySet()) {
			if (e.getValue()) {
				System.out.println("Analysis on " + e.getKey() + " successful.");
			} else {
				System.out.println("Analysis on " + e.getKey() + " failed.");
				failureReason.get(e.getKey()).printStackTrace();
			}
		}
	}

	public static List<AppSpec> listApps(String root) throws IOException, InterruptedException, BrutException {
		LinkedList<AppSpec> ret = new LinkedList<AppSpec>();
		addAllAppsTo(root, ret);
		return ret;
	}

	private static void addAllAppsTo(String root, List<AppSpec> base) throws IOException, InterruptedException, BrutException {
		File f = new File(root);
		if (f.isFile() && f.getName().endsWith("apk")) {
			File manifestFile = extractManifest(f);
			base.add(new AppSpec(f, manifestFile));
		} else if (f.isDirectory()) {
			for (File g : f.listFiles()) {
				addAllAppsTo(g.getAbsolutePath(), base);
			}
		}
	}

	public static File extractManifest(File apkFile) throws IOException, InterruptedException, BrutException {
		String[] args = new String[] {"d", "-f", apkFile.getAbsolutePath(), "-o", apkFile.getParent() + "/apktool"};
		System.out.println(Arrays.toString(args));
		brut.apktool.Main.main(args);
		return new File(apkFile.getParent() + "/apktool/AndroidManifest.xml");
	}

	public static void runAnalysisOn(AppSpec appSpec) {
		runAnalysisOn(Collections.singleton(appSpec));
	}
}
