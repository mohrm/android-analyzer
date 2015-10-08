package edu.kit.jodroid.ifc;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;

import edu.kit.jodroid.policy.IFCPolicy;

public class CHABasedSrcSnkScanner extends SrcSnkScanner {

	private AnalysisCache cache = new AnalysisCache(new DexIRFactory());

	public CHABasedSrcSnkScanner(IClassHierarchy cha, IFCPolicy policy) {
		super(cha, policy);
	}

	@Override
	protected boolean isCalledFromApplicationCode(MethodReference mr) {
		
		for (IClass cl : cha) {
			if (!cl.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
				continue;
			}
			for (IMethod m : cl.getDeclaredMethods()) {
				IR ir = cache.getIR(m);
				if (ir == null) continue;
				for (SSAInstruction i : ir.getInstructions()) {
					if (i instanceof SSAAbstractInvokeInstruction) {
						SSAAbstractInvokeInstruction invk = (SSAAbstractInvokeInstruction) i;
						MethodReference staticTarget = invk.getDeclaredTarget();
						if (mayOverride(mr, staticTarget)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private boolean mayOverride(MethodReference mr, MethodReference staticTarget) {
		if (!mr.getSelector().equals(staticTarget.getSelector())) {
			return false;
		}
		IClass testTgt = cha.lookupClass(mr.getDeclaringClass());
		IClass staticTgt = cha.lookupClass(staticTarget.getDeclaringClass());
		if (testTgt == null || staticTgt == null) {
			return false;
		} else {
			return cha.isAssignableFrom(staticTgt, testTgt);
		}
	}

}
