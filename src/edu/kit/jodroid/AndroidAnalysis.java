package edu.kit.jodroid;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;

import org.json.JSONException;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.dalvik.classLoader.DexFileModule;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.AndroidModel;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.parameters.DefaultInstantiationBehavior;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.structure.LoopKillAndroidModel;
import com.ibm.wala.dalvik.ipa.callgraph.impl.AndroidEntryPoint;
import com.ibm.wala.dalvik.ipa.callgraph.impl.AndroidEntryPoint.ExecutionOrder;
import com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.IntentContextInterpreter;
import com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.IntentContextSelector;
import com.ibm.wala.dalvik.util.AndroidEntryPointLocator;
import com.ibm.wala.dalvik.util.AndroidEntryPointLocator.LocatorFlags;
import com.ibm.wala.dalvik.util.AndroidEntryPointManager;
import com.ibm.wala.dalvik.util.AndroidManifestXMLReader;
import com.ibm.wala.dalvik.util.AndroidPreFlightChecks;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.pruned.ApplicationLoaderPolicy;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;

import edu.kit.joana.ifc.sdg.graph.SDG;
import edu.kit.joana.ifc.sdg.graph.SDGSerializer;
import edu.kit.joana.util.Pair;
import edu.kit.joana.wala.core.CGConsumer;
import edu.kit.joana.wala.core.ExternalCallCheck;
import edu.kit.joana.wala.core.SDGBuilder;
import edu.kit.joana.wala.core.SDGBuilder.ExceptionAnalysis;
import edu.kit.joana.wala.core.SDGBuilder.FieldPropagation;
import edu.kit.joana.wala.core.SDGBuilder.PointsToPrecision;
import edu.kit.joana.wala.core.SDGBuilder.SDGBuilderConfig;
import edu.kit.joana.wala.core.SDGBuilder.StaticInitializationTreatment;
import edu.kit.joana.wala.flowless.pointsto.AliasGraph;
import edu.kit.joana.wala.flowless.spec.java.ast.MethodInfo;
import edu.kit.jodroid.ifc.AndroidIFCAnalysis;
import edu.kit.jodroid.ifc.PrepareAnnotation;
import edu.kit.jodroid.ifc.SrcSnkScanner;
import edu.kit.jodroid.ifc.SrcSnkScanner.ScanResult;
import edu.kit.jodroid.io.AppSpec;
import edu.kit.jodroid.io.ParsePolicyFromJSON;
import edu.kit.jodroid.policy.IFCPolicy;

public class AndroidAnalysis {
	public static final String JDK_STUBS = "jdkstubs";
	public static final String ANDROID_LIB = "androidlib";
	public static final String POLICY_TEMPLATE = "policytemplate";

	static Properties properties;

	static {
		File file = new File(AndroidAnalysis.class.getClassLoader().getResource("jodroid.properties").getFile());
		FileInputStream fileInput = null;
		try {
			fileInput = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		properties = new Properties();
		try {
			properties.load(fileInput);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		if (properties.getProperty(JDK_STUBS) == null) {
			throw new RuntimeException("Specify jdkstubs=<path to jdk stubs jar>!");
		}
		if (properties.getProperty(ANDROID_LIB) == null) {
			throw new RuntimeException("Specify androidlib=<path to android lib jar>!");
		}
		if (properties.getProperty(POLICY_TEMPLATE) == null) {
			throw new RuntimeException("Specify policytemplate=<path to policy template>!");
		}
	}

	public static class CallGraphKeeper implements CGConsumer {
		private CallGraph callGraph;

		@Override
		public void consume(CallGraph cg, PointerAnalysis<? extends InstanceKey> pts) {
			this.callGraph = cg;
		}

		public CallGraph getCallGraph() {
			return callGraph;
		}
	}

	private static TypeReference JavaLangRunnable = TypeReference.findOrCreate(ClassLoaderReference.Primordial, "Ljava/lang/Runnable");
	private static TypeReference WebViewClient = TypeReference.findOrCreate(ClassLoaderReference.Primordial, "Landroid/webkit/WebViewClient");
	private static Selector run = Selector.make("run()V");

	public AndroidIFCAnalysis prepareAnalysis(File apkFile) throws IOException, CancelException, UnsoundGraphException, WalaException, JSONException, InterruptedException, APKToolException {
		File manifestFile = MainAnalysis.extractManifest(apkFile);
		return prepareAnalysis(new AppSpec(apkFile, manifestFile));
	}

	public Set<Pair<String, String>> runAnalysis(File apkFile) throws IOException, CancelException, UnsoundGraphException, WalaException, JSONException, InterruptedException, APKToolException {
		AndroidIFCAnalysis a = prepareAnalysis(apkFile);
		return a.check().getFlows();
	}

	public SDGBuilder.SDGBuilderConfig makeSDGBuilderConfig(AppSpec appSpec, CGConsumer consumer, boolean onlyCG) throws ClassHierarchyException, IOException, CancelException {
		AnalysisScope scope = makeMinimalScope(appSpec);
		IClassHierarchy cha = ClassHierarchy.make(scope);
		AnalysisCache cache = new AnalysisCache(new DexIRFactory());
		AnalysisOptions options = configureOptions(scope, cha);
		populateEntryPoints(cha);
		if (appSpec.manifestFile != null) {
			new AndroidManifestXMLReader(appSpec.manifestFile);
		}
		System.out.println(AndroidEntryPointManager.MANAGER.getSeen());
		IMethod lifecycle;
		new AndroidPreFlightChecks(AndroidEntryPointManager.MANAGER, options, cha).all();
		AndroidEntryPointManager.MANAGER.setModelBehavior(LoopKillAndroidModel.class);
		final AndroidModel modeller = new AndroidModel(cha, options, cache);
		lifecycle = modeller.getMethodEncap();
		final SDGBuilder.SDGBuilderConfig scfg = new SDGBuilder.SDGBuilderConfig();
		scfg.out = System.out;
		scfg.scope = scope;
		scfg.cache = cache;
		scfg.cha = cha;
		scfg.entry = lifecycle;
		scfg.ext = new ExternalCallCheck() {
			@Override
			public boolean isCallToModule(SSAInvokeInstruction invk) {
				return false;
			}

			@Override
			public void registerAliasContext(SSAInvokeInstruction invk, int callNodeId, AliasGraph.MayAliasGraph context) {
			}

			@Override
			public void setClassHierarchy(IClassHierarchy cha) {
			}

			@Override
			public MethodInfo checkForModuleMethod(IMethod im) {
				return null;
			}

			@Override
			public boolean resolveReflection() {
				return false;
			}
		};
		scfg.exceptions = ExceptionAnalysis.INTERPROC;
		scfg.prunecg = 2;
		scfg.pruningPolicy = ApplicationLoaderPolicy.INSTANCE;
		scfg.pts = PointsToPrecision.INSTANCE_BASED;
		scfg.staticInitializers = StaticInitializationTreatment.SIMPLE;
		scfg.fieldPropagation = FieldPropagation.OBJ_GRAPH;
		scfg.computeInterference = false;
		scfg.computeAllocationSites = false;
		scfg.cgConsumer = consumer;
		scfg.additionalContextSelector = new IntentContextSelector(cha);
		scfg.additionalContextInterpreter = new IntentContextInterpreter(cha, options, cache);
		scfg.localKillingDefs = false;
		scfg.abortAfterCG = !onlyCG;
		return scfg;
	}

	public AndroidIFCAnalysis prepareAnalysis(AppSpec appSpec) throws IOException, CancelException, UnsoundGraphException, WalaException, JSONException {
		CallGraphKeeper keeper = new CallGraphKeeper();
		SDGBuilderConfig scfg = makeSDGBuilderConfig(appSpec, keeper, false);
		SDG sdg = SDGBuilder.build(scfg);
		SDGSerializer.toPDGFormat(sdg, new FileOutputStream(appSpec.apkFile.getParent() + "/app.pdg"));
		IFCPolicy policy = new ParsePolicyFromJSON(loadJSONPolicy()).run();
		PrepareAnnotation prepare = new PrepareAnnotation(keeper.getCallGraph(), sdg, policy);
		AndroidIFCAnalysis ifc = new AndroidIFCAnalysis(sdg, prepare.computeAnnotation());
		return ifc;
	}

	public ScanResult justScan(AppSpec appSpec) throws IOException, CancelException, UnsoundGraphException, WalaException, JSONException {
		CallGraphKeeper keeper = new CallGraphKeeper();
		SDGBuilderConfig scfg = makeSDGBuilderConfig(appSpec, keeper, true);
		SDG sdg = SDGBuilder.build(scfg);
		IFCPolicy policy = new ParsePolicyFromJSON(loadJSONPolicy()).run();
		SrcSnkScanner scanner = new SrcSnkScanner(keeper.getCallGraph(), policy);
		return scanner.scan();
	}

	public static AnalysisScope makeMinimalScope(AppSpec appSpec) throws IOException {
		AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();
		scope.setLoaderImpl(ClassLoaderReference.Primordial, "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");
		scope.setLoaderImpl(ClassLoaderReference.Application, "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");
		scope.addToScope(ClassLoaderReference.Primordial, new JarFileModule(loadJDKStubs()));
		scope.addToScope(ClassLoaderReference.Primordial, new JarFileModule(loadAndroidLib()));
		scope.addToScope(ClassLoaderReference.Application, DexFileModule.make(appSpec.apkFile));
		return scope;
	}

	public static JarFile loadJDKStubs() throws IOException {
		String jdkStubs = properties.getProperty(JDK_STUBS);
		return new JarFile(AndroidAnalysis.class.getClassLoader().getResource(jdkStubs).getFile());
	}

	public static JarFile loadAndroidLib() throws IOException {
		String androidLib = properties.getProperty(ANDROID_LIB);
		return new JarFile(AndroidAnalysis.class.getClassLoader().getResource(androidLib).getFile());
	}

	public static File loadJSONPolicy() throws IOException {
		String jsonPolicy = properties.getProperty(POLICY_TEMPLATE);
		return new File(AndroidAnalysis.class.getClassLoader().getResource(jsonPolicy).getFile());
	}
	private AnalysisOptions configureOptions(AnalysisScope scope, IClassHierarchy cha) {
		AnalysisOptions options = new AnalysisOptions(scope, null);
		options.setReflectionOptions(ReflectionOptions.FULL);
		AndroidEntryPointManager.reset();
		AndroidEntryPointManager.MANAGER.setInstantiationBehavior(new DefaultInstantiationBehavior(cha));
		AndroidEntryPointManager.MANAGER.setDoBootSequence(false);
		Util.addDefaultSelectors(options, cha);
		Util.addDefaultBypassLogic(options, scope, Util.class.getClassLoader(), cha);
		return options;
	}

	private void populateEntryPoints(IClassHierarchy cha) {
		Set<AndroidEntryPointLocator.LocatorFlags> entrypointLocatorFlags = EnumSet.noneOf(AndroidEntryPointLocator.LocatorFlags.class);
		entrypointLocatorFlags.add(LocatorFlags.INCLUDE_CALLBACKS);
		final AndroidEntryPointLocator epl = new AndroidEntryPointLocator(entrypointLocatorFlags);
		AndroidEntryPointManager.ENTRIES = epl.getEntryPoints(cha);
		for (IClass cl : cha.getImplementors(AndroidAnalysis.JavaLangRunnable)) {
			if (cl.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
				AndroidEntryPointManager.ENTRIES.add(new AndroidEntryPoint(ExecutionOrder.MIDDLE_OF_LOOP, cl.getMethod(AndroidAnalysis.run), cha));
			}
		}
		IClass webview = cha.lookupClass(AndroidAnalysis.WebViewClient);
		for (IClass cl : cha) {
			if (cha.isAssignableFrom(webview, cl)) {
				for (IMethod m : cl.getDeclaredMethods()) {
					AndroidEntryPointManager.ENTRIES.add(new AndroidEntryPoint(ExecutionOrder.MIDDLE_OF_LOOP, m, cha));
				}
			}
		}
		Collections.sort(AndroidEntryPointManager.ENTRIES, new AndroidEntryPoint.ExecutionOrderComperator());
		AndroidEntryPointManager.ENTRIES = Collections.unmodifiableList(AndroidEntryPointManager.ENTRIES);
	}

}
