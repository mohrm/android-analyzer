package edu.kit.jodroid.ifc;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import policy.IFCPolicy;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

import edu.kit.joana.api.annotations.AnnotationType;
import edu.kit.joana.api.sdg.SDGCall;
import edu.kit.joana.api.sdg.SDGCallPart;
import edu.kit.joana.api.sdg.SDGFormalParameter;
import edu.kit.joana.api.sdg.SDGMethod;
import edu.kit.joana.api.sdg.SDGProgram;
import edu.kit.joana.api.sdg.SDGProgramPart;
import edu.kit.joana.ifc.sdg.graph.SDG;
import edu.kit.joana.ifc.sdg.graph.SDGNode;
import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;
import edu.kit.jodroid.io.ParsePolicyFromJSON;
import edu.kit.jodroid.io.ParsePolicyFromJSON.Sink;
import edu.kit.jodroid.io.ParsePolicyFromJSON.Source;
import edu.kit.jodroid.io.ParsePolicyFromJSON.SourceOrSink;
import edu.kit.jodroid.io.ParsePolicyFromJSON.SourceOrSink.Visitor;

public class PrepareAnnotation {
	private CallGraph callGraph;
	private SDGProgram program;
	private IFCPolicy policy;
	
	public PrepareAnnotation(CallGraph callGraph, SDG sdg, IFCPolicy policy) {
		this.callGraph = callGraph;
		this.program = new SDGProgram(sdg);
		this.policy = policy;
	}

	public AnnotationPolicy computeAnnotation() {
		CompletedSignatures completedSigs = getCompletedSignatures(policy.getSourcesAndSinks());
		final Map<String, Set<SDGNode>> cat2SrcNodes = new HashMap<String, Set<SDGNode>>();
		final Map<String, Set<SDGNode>> cat2SnkNodes = new HashMap<String, Set<SDGNode>>();
		for (Map.Entry<SourceOrSink, JavaMethodSignature> e : completedSigs.m.entrySet()) {
			if (e.getKey().isParameterOfEntryPoint()) {
				IClassHierarchy cha = callGraph.getClassHierarchy();
				IClass c = cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Primordial,
						e.getValue().getDeclaringType().toBCString(false)));
				if (c == null) {
					System.out.println(e.getValue().getDeclaringType());
					continue;
				}
				for (IClass c0 : cha) {
					if (c0.getClassLoader().getReference().equals(ClassLoaderReference.Application) && cha.isAssignableFrom(c, c0)) {
						IMethod m = c0.getMethod(Selector.make(e.getValue().getSelector()));
						if (m != null) {
							for (SDGMethod sdgMethod : program.getMethods(JavaMethodSignature.fromString(m.getSignature()))) {
								int p = e.getKey().getParams()[0];
								Set<SDGProgramPart> frmParams = new HashSet<SDGProgramPart>();
								if (p >= 0) {
									frmParams.add(sdgMethod.getParameter(p));
								} else if (p == ParsePolicyFromJSON.SourceOrSink.THIS_PARAM) {
									frmParams.add(sdgMethod.getParameter(0));
								} else if (p == ParsePolicyFromJSON.SourceOrSink.RET_PARAM) {
									frmParams.add(sdgMethod.getExit());
								} else if (p == ParsePolicyFromJSON.SourceOrSink.ALL_ARGUMENTS) {
									for (SDGFormalParameter f : sdgMethod.getParameters()) {
										frmParams.add(f);
									}
									frmParams.add(sdgMethod.getExit());
								} else {
									throw new RuntimeException("Illegal parameter value: " + p);
								}
								Set<SDGNode> base = cat2SrcNodes.get(e.getKey().getCategory());
								if (base == null) {
									base = new HashSet<SDGNode>();
									cat2SrcNodes.put(e.getKey().getCategory(), base);
								}
								for (SDGProgramPart fp : frmParams) {
									base.addAll(program.getNodeCollector().collectNodes(fp, AnnotationType.SOURCE));
									base.addAll(program.getNodeCollector().collectNodes(fp, AnnotationType.SINK));
								}
							}
						}
					}
				}
			} else {
				for (final SDGCall c : program.getCallsToMethod(e.getValue())) {
					e.getKey().accept(new Visitor() {

						@Override
						public void visitSource(Source src) {
							Set<SDGNode> nodes = new HashSet<SDGNode>();
							for (SDGCallPart cp : translate(src)) {
								nodes.addAll(program.getNodeCollector().collectNodes(cp, AnnotationType.SOURCE));
							}
							Set<SDGNode> base = lookupOrRegister(src.getCategory(), cat2SrcNodes);
							base.addAll(nodes);
						}

						@Override
						public void visitSink(Sink snk) {
							Set<SDGNode> nodes = new HashSet<SDGNode>();
							for (SDGCallPart cp : translate(snk)) {
								nodes.addAll(program.getNodeCollector().collectNodes(cp, AnnotationType.SINK));
							}
							Set<SDGNode> base = lookupOrRegister(snk.getCategory(), cat2SnkNodes);
							base.addAll(nodes);
						}

						private Set<SDGCallPart> translate(SourceOrSink poi) {
							Set<SDGCallPart> actParams = new HashSet<SDGCallPart>();
							int p = poi.getParams()[0];
							if (p < 0) {
								switch (p) {
								case ParsePolicyFromJSON.SourceOrSink.THIS_PARAM:
									if (c.getThis() != null)
										actParams.add(c.getThis());
									break;
								case ParsePolicyFromJSON.SourceOrSink.RET_PARAM:
									if (c.getReturn() != null)
										actParams.add(c.getReturn());
									break;
								case ParsePolicyFromJSON.SourceOrSink.ALL_ARGUMENTS:
									actParams.addAll(c.getActualParameters());
									if (c.getThis() != null)
										actParams.add(c.getThis());
									if (c.getReturn() != null)
										actParams.add(c.getReturn());
									break;
								}
							} else {
								actParams.add(c.getActualParameter(p));
							}
							return actParams;
						}

						private Set<SDGNode> lookupOrRegister(String category, Map<String, Set<SDGNode>> m) {
							Set<SDGNode> ret = m.get(category);
							if (ret == null) {
								ret = new HashSet<SDGNode>();
								m.put(category, ret);
							}
							return ret;
						}
					});
				}
			}
		}
		
		return new AnnotationPolicy(cat2SrcNodes, cat2SnkNodes, completedSigs);
	}

	private CompletedSignatures getCompletedSignatures(List<SourceOrSink> pois) {
		IClassHierarchy cha = callGraph.getClassHierarchy();
		int noOverall = 0;
		int noMatched = 0;
		Map<SourceOrSink, JavaMethodSignature> completedSigs = new HashMap<SourceOrSink, JavaMethodSignature>();
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
					completedSigs.put(poi, JavaMethodSignature.fromString(m.getSignature()));
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
		public final Map<SourceOrSink, JavaMethodSignature> m;
		public final int noMatched;
		public final int noOverall;
		public final List<SourceOrSink> unmatched;
		
		public CompletedSignatures(Map<SourceOrSink, JavaMethodSignature> m, int noMatched, int noOverall, List<SourceOrSink> unmatched) {
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
}
