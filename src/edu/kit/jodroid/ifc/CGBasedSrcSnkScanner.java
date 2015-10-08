package edu.kit.jodroid.ifc;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;

import edu.kit.jodroid.io.ParsePolicyFromJSON.Sink;
import edu.kit.jodroid.io.ParsePolicyFromJSON.Source;
import edu.kit.jodroid.io.ParsePolicyFromJSON.SourceOrSink;
import edu.kit.jodroid.policy.IFCPolicy;

public class CGBasedSrcSnkScanner extends SrcSnkScanner {
	private CallGraph callGraph;

	public CGBasedSrcSnkScanner(CallGraph callGraph, IFCPolicy policy) {
		super(callGraph.getClassHierarchy(), policy);
		this.callGraph = callGraph;
	}

	/* (non-Javadoc)
	 * @see edu.kit.jodroid.ifc.SrcSnkScanner#isCalledFromApplicationCode(com.ibm.wala.types.MethodReference)
	 */
	@Override
	protected boolean isCalledFromApplicationCode(MethodReference mr) {
		for (CGNode n : callGraph.getNodes(mr)) {
			Iterator<CGNode> callers = callGraph.getPredNodes(n);
			while (callers.hasNext()) {
				CGNode caller = callers.next();
				if (caller.getMethod().getDeclaringClass().getReference().getClassLoader().equals(ClassLoaderReference.Application)) {
					// n is called from application code
					return true;
				}
			}
		}
		return false;
	}

	public static class CompletedSignatures {
		public final Map<SourceOrSink, MethodReference> m;
		public final int noMatched;
		public final int noOverall;
		public final List<SourceOrSink> unmatched;

		public CompletedSignatures(Map<SourceOrSink, MethodReference> m, int noMatched, int noOverall, List<SourceOrSink> unmatched) {
			this.m = m;
			this.noMatched = noMatched;
			this.noOverall = noOverall;
			this.unmatched = unmatched;
		}

		public void printStatistics(PrintStream out) {
			out.println(String.format("matched %d of %d pois.", noMatched, noOverall));
			if (!unmatched.isEmpty()) {
				out.println("unmatched source methods:");
				for (SourceOrSink unmSrc : unmatched) {
					out.println(unmSrc.getDeclaringClass() + " " + unmSrc.getMethod());
				}
			}
		}
	}
	public static class ScanResult {
		public Set<Source> sources;
		public Set<Sink> sinks;
		public ScanResult(Set<Source> sources, Set<Sink> sinks) {
			this.sources = sources;
			this.sinks = sinks;
		}
		public void print(PrintStream out) {
			out.println("Found the following sources:");
			for (Source s : sources) {
				System.out.println(s);
			}
			out.println("Found the following sinks:");
			for (Sink s : sinks) {
				System.out.println(s);
			}
		}
	}
}
