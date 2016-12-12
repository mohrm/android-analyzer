package edu.kit.jodroid;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.json.JSONException;

import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;

import edu.kit.joana.util.Pair;
import edu.kit.joana.wala.jodroid.io.apktool.APKToolException;

public class ShortAnalysis {

	public static void main(String[] args) throws IOException, CancelException, UnsoundGraphException, WalaException, JSONException, InterruptedException, APKToolException {
		AndroidAnalysis a = new AndroidAnalysis();
		Set<Pair<String, String>> flows = a.runAnalysis(new File(args[0]));
		System.out.println("found flows:");
		for (Pair<String, String> f : flows) {
			System.out.println(String.format("%s --> %s", f.getFirst(), f.getSecond()));
		}
	}

}
