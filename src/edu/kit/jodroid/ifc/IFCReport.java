package edu.kit.jodroid.ifc;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Map;

import edu.kit.joana.ifc.sdg.core.violations.ClassifiedViolation;
import edu.kit.joana.util.Pair;

public class IFCReport {
	private AndroidIFCAnalysis ifcAnalysis;
	private Map<Pair<String, String>, Collection<ClassifiedViolation>> vioMap;

	public IFCReport(AndroidIFCAnalysis androidIFCAnalysis, Map<Pair<String, String>, Collection<ClassifiedViolation>> vioMap) {
		this.vioMap = vioMap;
		this.ifcAnalysis = androidIFCAnalysis;
	}

	public void print(PrintStream out) {
		ifcAnalysis.printAnnotationDebug(out);
		out.println();
		out.println("IFC report:");
		if (vioMap.entrySet().isEmpty()) {
			out.println("~~~ no flows found ~~~");
		} else {
			for (Map.Entry<Pair<String, String>, Collection<ClassifiedViolation>> reportEntry : vioMap.entrySet()) {
				if (reportEntry.getValue().size() > 0) {
					out.println(String.format("%s --> %s: %d", reportEntry.getKey().getFirst(), reportEntry.getKey().getSecond(), reportEntry
							.getValue().size()));
				}
			}
		}
	}
}
