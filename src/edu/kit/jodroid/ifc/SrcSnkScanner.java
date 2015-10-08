package edu.kit.jodroid.ifc;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

import edu.kit.jodroid.ifc.CGBasedSrcSnkScanner.CompletedSignatures;
import edu.kit.jodroid.ifc.CGBasedSrcSnkScanner.ScanResult;
import edu.kit.jodroid.io.ParsePolicyFromJSON.Sink;
import edu.kit.jodroid.io.ParsePolicyFromJSON.Source;
import edu.kit.jodroid.io.ParsePolicyFromJSON.SourceOrSink;
import edu.kit.jodroid.io.ParsePolicyFromJSON.SourceOrSink.Visitor;
import edu.kit.jodroid.policy.IFCPolicy;

public abstract class SrcSnkScanner {

	protected IClassHierarchy cha;
	protected IFCPolicy policy;
	
	public SrcSnkScanner(IClassHierarchy cha, IFCPolicy policy) {
		this.cha = cha;
		this.policy = policy;
	}

	protected abstract boolean isCalledFromApplicationCode(MethodReference mr);

	protected CompletedSignatures getCompletedSignatures(List<SourceOrSink> pois) {
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

}