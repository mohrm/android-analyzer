package edu.kit.jodroid;
import java.io.IOException;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;

import edu.kit.jodroid.io.AppSpec;

public class InspectIntentStrings {
    private static String[] intentMethods = { "getBooleanArrayExtra(Ljava/lang/String;)",
	    "getBooleanExtra(Ljava/lang/String;Z)", "getBundleExtra(Ljava/lang/String;)",
	    "getByteArrayExtra(Ljava/lang/String;)", "getByteExtra(Ljava/lang/String;B)",
	    "getCharArrayExtra(Ljava/lang/String;)", "getCharExtra(Ljava/lang/String;C)",
	    "getCharSequenceArrayExtra(Ljava/lang/String;)", "getCharSequenceArrayListExtra(Ljava/lang/String;)",
	    "getCharSequenceExtra(Ljava/lang/String;)", "getDoubleArrayExtra(Ljava/lang/String;)",
	    "getDoubleExtra(Ljava/lang/String;D)", "getFloatArrayExtra(Ljava/lang/String;)",
	    "getFloatExtra(Ljava/lang/String;F)", "getIntArrayExtra(Ljava/lang/String;)",
	    "getIntExtra(Ljava/lang/String;I)", "getIntegerArrayListExtra(Ljava/lang/String;)",
	    "getLongArrayExtra(Ljava/lang/String;)", "getLongExtra(Ljava/lang/String;J)",
	    "getParcelableArrayExtra(Ljava/lang/String;)", "getParcelableArrayListExtra(Ljava/lang/String;)",
	    "getParcelableExtra(Ljava/lang/String;)", "getSerializableExtra(Ljava/lang/String;)",
	    "getShortArrayExtra(Ljava/lang/String;)", "getShortExtra(Ljava/lang/String;S)",
	    "getStringArrayExtra(Ljava/lang/String;)", "getStringArrayListExtra(Ljava/lang/String;)",
	    "getStringExtra(Ljava/lang/String;)" };

    public static void main(String[] args) throws IOException, ClassHierarchyException {
	for (AppSpec appSpec : MainAnalysis.listApps("examples/apps")) {
	    System.out.println(appSpec.apkFile + ":");
	    AnalysisScope scope = AndroidAnalysis.makeMinimalScope(appSpec);
	    IClassHierarchy cha = ClassHierarchy.make(scope);
	    AnalysisCache cache = new AnalysisCache(new DexIRFactory());
	    int noIntentMethods = 0;
	    int noStrLiteral = 0;
	    for (IClass cl : cha) {
		if (!cl.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
		    continue;
		}
		for (IMethod m : cl.getDeclaredMethods()) {
		    IR ir = cache.getIR(m);
		    if (ir == null) {
			continue;
		    } else {
			SymbolTable symbol = ir.getSymbolTable();
			for (SSAInstruction i : ir.getInstructions()) {
			    if (i == null) {
				continue;
			    } else if (i instanceof SSAInvokeInstruction) {
				SSAInvokeInstruction invk = (SSAInvokeInstruction) i;
				if (couldBeIntentMethod(invk.getDeclaredTarget())) {
				    noIntentMethods++;
				    int arg = invk.getUse(1);
				    if (symbol.isConstant(arg)) {
					noStrLiteral++;
				    }
				}
			    }
			}
		    }
		}
	    }
	    System.out.println(String.format("%d of %d intent calls had a string literal!", noStrLiteral, noIntentMethods));
	}
    }

    private static boolean couldBeIntentMethod(MethodReference target) {
	String selector = target.getSelector().toString();
	for (String intentMethod : intentMethods) {
	    if (selector.contains(intentMethod)) {
		return true;
	    }
	}
	return false;
    }
}
