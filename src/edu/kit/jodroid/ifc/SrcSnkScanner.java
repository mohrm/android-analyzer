package edu.kit.jodroid.ifc;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

import edu.kit.jodroid.io.ParsePolicyFromJSON.Sink;
import edu.kit.jodroid.io.ParsePolicyFromJSON.Source;
import edu.kit.jodroid.io.ParsePolicyFromJSON.SourceOrSink;
import edu.kit.jodroid.io.ParsePolicyFromJSON.SourceOrSink.Visitor;
import edu.kit.jodroid.policy.IFCPolicy;

public class SrcSnkScanner {
	private CallGraph callGraph;
	private IFCPolicy policy;

	public SrcSnkScanner(CallGraph callGraph, IFCPolicy policy) {
		this.callGraph = callGraph;
		this.policy = policy;
	}

	public ScanResult scan() {
		final Set<Source> sources = new LinkedHashSet<Source>();
		final Set<Sink> sinks = new LinkedHashSet<Sink>();
		for (Entry<SourceOrSink, MethodReference> e : getCompletedSignatures(policy.getSourcesAndSinks()).m.entrySet()) {
			if (isCalledFromApplicationCode(e.getValue())) {
				e.getKey().accept(new Visitor() {

					@Override
					public void visitSource(Source src) {
						sources.add(src);
					}

					@Override
					public void visitSink(Sink snk) {
						sinks.add(snk);
					}
				});
			}
		}
		return new ScanResult(sources, sinks);
	}

	private boolean isCalledFromApplicationCode(MethodReference mr) {
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

	private CompletedSignatures getCompletedSignatures(List<SourceOrSink> pois) {
		IClassHierarchy cha = callGraph.getClassHierarchy();
		int noOverall = 0;
		int noMatched = 0;
		Map<SourceOrSink, MethodReference> completedSigs = new HashMap<SourceOrSink, MethodReference>();
		List<SourceOrSink> unmatched = new LinkedList<SourceOrSink>();
		for (SourceOrSink poi : pois) {
			String declaringClass = poi.getDeclaringClass();
			String incompleteSelector = poi.getMethod();
			IClass c = cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Primordial, declaringClass.replaceAll(";", "")));
			if (c == null) {
				throw new RuntimeException(declaringClass);
			}
			boolean matched = false;
			for (IMethod m : c.getAllMethods()) {
				TypeName retTypeName = m.getSelector().getDescriptor().getReturnType();
				String retType = m.getSelector().getDescriptor().getReturnType().toString();
				String completedSelector = incompleteSelector + retType;

				if (retTypeName.isClassType() || (retTypeName.isArrayType() && retTypeName.getInnermostElementType().isClassType())) {
					completedSelector += ";";
				}
				if (m.getSelector().toString().equals(completedSelector)) {
					completedSigs.put(poi, m.getReference());
					noMatched++;
					matched = true;
					break;
				}
			}
			if (!matched) {
				unmatched.add(poi);
				for (IMethod m : c.getAllMethods()) {
					System.out.println(m.getSignature());
				}
			}
			noOverall++;
		}

		return new CompletedSignatures(completedSigs, noMatched, noOverall, unmatched);
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
