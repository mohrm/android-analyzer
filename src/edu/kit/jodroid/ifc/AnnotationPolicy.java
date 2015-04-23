package edu.kit.jodroid.ifc;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;

import edu.kit.joana.ifc.sdg.graph.SDGNode;
import edu.kit.jodroid.ifc.PrepareAnnotation.CompletedSignatures;


public class AnnotationPolicy {

	private final Map<String, Set<SDGNode>> cat2SrcNodes;
	private final Map<String, Set<SDGNode>> cat2SnkNodes;
	private PrepareAnnotation.CompletedSignatures completedSigs;

	public AnnotationPolicy(Map<String, Set<SDGNode>> cat2SrcNodes, Map<String, Set<SDGNode>> cat2SnkNodes, PrepareAnnotation.CompletedSignatures completedSigs) {
		this.cat2SrcNodes = cat2SrcNodes;
		this.cat2SnkNodes = cat2SnkNodes;
		this.completedSigs = completedSigs;
	}

	public Map<String, Set<SDGNode>> getCat2SrcNodes() {
		return cat2SrcNodes;
	}

	public Map<String, Set<SDGNode>> getCat2SnkNodes() {
		return cat2SnkNodes;
	}
	
	public void print(PrintStream out) {
		completedSigs.printStatistics(out);
		out.println("will annotate the following sources");
		if (cat2SrcNodes.isEmpty()) {
			out.println("~~~ none ~~~");
		} else {
			for (Map.Entry<String, Set<SDGNode>> srcEntry : cat2SrcNodes.entrySet()) {
				out.println(String.format("%s: %s", srcEntry.getKey(), srcEntry.getValue().toString()));
			}
		}
		out.println("will annotate the following sinks");
		if (cat2SnkNodes.isEmpty()) {
			out.println("~~~ none ~~~");
		} else {
			for (Map.Entry<String, Set<SDGNode>> snkEntry : cat2SnkNodes.entrySet()) {
				out.println(String.format("%s: %s", snkEntry.getKey(), snkEntry.getValue().toString()));
			}
		}
	}

}
