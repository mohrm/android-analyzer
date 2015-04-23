package edu.kit.jodroid.ifc;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import edu.kit.joana.api.lattice.BuiltinLattices;
import edu.kit.joana.ifc.sdg.core.SecurityNode;
import edu.kit.joana.ifc.sdg.core.SlicingBasedIFC;
import edu.kit.joana.ifc.sdg.core.violations.ClassifiedViolation;
import edu.kit.joana.ifc.sdg.graph.SDG;
import edu.kit.joana.ifc.sdg.graph.SDGNode;
import edu.kit.joana.ifc.sdg.graph.slicer.conc.I2PBackward;
import edu.kit.joana.ifc.sdg.graph.slicer.conc.I2PForward;
import edu.kit.joana.util.Pair;

public class AndroidIFCAnalysis {

	private SDG sdg;
	private AnnotationPolicy annPolicy;
	

	public AndroidIFCAnalysis(SDG sdg, AnnotationPolicy annPolicy) {
		this.sdg = sdg;
		this.annPolicy = annPolicy;
	}
	
	public void printAnnotationDebug(PrintStream out) {
		annPolicy.print(out);
	}
	
	public SDG getSDG() {
		return sdg;
	}

	public IFCReport check() {
		Map<Pair<String, String>, Collection<ClassifiedViolation>> vioMap = new HashMap<Pair<String, String>, Collection<ClassifiedViolation>>();
		for (Map.Entry<String, Set<SDGNode>> snkEntry : annPolicy.getCat2SnkNodes().entrySet()) {
			for (Map.Entry<String, Set<SDGNode>> srcEntry : annPolicy.getCat2SrcNodes().entrySet()) {
				SlicingBasedIFC ifc = new SlicingBasedIFC(sdg, BuiltinLattices.getBinaryLattice(), new I2PForward(sdg), new I2PBackward(sdg));
				for (SDGNode src : srcEntry.getValue()) {
					((SecurityNode) src).setProvided(BuiltinLattices.STD_SECLEVEL_HIGH);
				}
				for (SDGNode snk : snkEntry.getValue()) {
					((SecurityNode) snk).setRequired(BuiltinLattices.STD_SECLEVEL_LOW);
				}
				Collection<ClassifiedViolation> vios = ifc.checkIFlow();
				if (!vios.isEmpty()) {
					vioMap.put(Pair.pair(srcEntry.getKey(), snkEntry.getKey()), vios);
				}
				for (SDGNode src : srcEntry.getValue()) {
					((SecurityNode) src).setProvided(null);
				}
				for (SDGNode snk : snkEntry.getValue()) {
					((SecurityNode) snk).setRequired(null);
				}
			}
		}
		return new IFCReport(this, vioMap);
	}
}
