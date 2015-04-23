package edu.kit.jodroid.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import policy.IFCPolicy;


public class ParsePolicyFromJSON {
	
	public static abstract class SourceOrSink {
		public static interface Visitor {
			void visitSource(Source src);
			void visitSink(Sink snk);
		}
		protected String category;
		protected String declaringClass;
		protected String method;
		protected int[] params;

		public static final int THIS_PARAM = 0;
		public static final int RET_PARAM = -1;
		public static final int ALL_ARGUMENTS = -2;
		public static final String UNKNOWN_CATEGORY = "___UNKNOWN___";
		
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("Kind: " + kind() + " ");
			sb.append("Category: " + category + " ");
			sb.append("Class: " + declaringClass + " ");
			sb.append("Method: " + method +" Params: " + params2String() + "\n");
			sb.append(specific());
			return sb.toString();
		}
		
		protected String specific() {
			return "";
		}
		
		protected abstract String kind();
		
		protected String params2String() {
			if (params.length == 0) {
				return "[]";
			} else {
				StringBuilder sb = new StringBuilder();
				sb.append("[");
				sb.append(param2String(params[0]));
				for (int i = 1; i < params.length; i++) {
					sb.append(", " + param2String(params[i]));
				}
				sb.append("]");
				return sb.toString();
			}
		}

		public String getCategory() {
			return category;
		}

		public String getDeclaringClass() {
			return declaringClass;
		}
		
		public boolean isParameterOfEntryPoint() {
			return false;
		}

		public String getMethod() {
			return method;
		}

		public int[] getParams() {
			return params;
		}

		public String param2String(int p) {
			switch (p) {
			case THIS_PARAM:
				return "this";
			case RET_PARAM:
				return "ret";
			case ALL_ARGUMENTS:
				return "all arguments";
			default:
				return Integer.toString(p);
			}
		}
		
		public abstract void accept(Visitor v);
	}
	
	public static class Source extends SourceOrSink implements Cloneable {
		protected Set<Integer> sensitiveOn = new HashSet<Integer>();
		protected boolean isEntryPointSource = false;
		
		@Override
		public Source clone() {
			Source s = new Source();
			s.category = category;
			s.declaringClass = declaringClass;
			s.isEntryPointSource = isEntryPointSource;
			s.method = method;
			s.params = params;
			s.sensitiveOn = sensitiveOn;
			return s;
		}
		
		public Source withCategory(String cat) {
			Source s = clone();
			s.category = cat;
			return s;
		}
		
		protected String kind() {
			return "Source";
		}
		
		protected String specific() {
			return "Sensitive on args: " + Arrays.toString(sensitiveOn.toArray(new Integer[0]));
		}
		
		@Override
		public boolean isParameterOfEntryPoint() {
			return isEntryPointSource;
		}
		@Override
		public void accept(Visitor v) {
			v.visitSource(this);
		}
	}
	
	public static class Sink extends SourceOrSink implements Cloneable {
		protected String kind() {
			return "Sink";
		}
		
		@Override
		public Sink clone() {
			Sink s = new Sink();
			s.category = category;
			s.declaringClass = declaringClass;
			s.method = method;
			s.params = params;
			return s;
		}

		public Sink withCategory(String cat) {
			Sink s = clone();
			s.category = cat;
			return s;
		}
		
		@Override
		public void accept(Visitor v) {
			v.visitSink(this);
		}
	}
	
	public static class ForbiddenThings {
		Map<String, Set<String>> forbiddenStrings = new HashMap<String, Set<String>>();
		Map<String, Set<String>> forbiddenUris = new HashMap<String, Set<String>>();
		
		public void addUris(String category, Set<String> uris) {
			addTo(category, uris, forbiddenUris);
		}
		public void addStrings(String category, Set<String> strings) {
			addTo(category, strings, forbiddenStrings);
		}
		void addTo(String category, Set<String> things, Map<String, Set<String>> m) {
			Set<String> base = m.get(category);
			if (base == null) {
				base = new HashSet<String>();
				m.put(category, base);
			}
			base.addAll(things);
		}
		public Set<String> getAllForbiddenStrings() {
			return getAll(forbiddenStrings);
		}
		public Set<String> getAllForbiddenUris() {
			return getAll(forbiddenUris);
		}
		
		Set<String> getAll(Map<String, Set<String>> m) {
			Set<String> ret = new HashSet<String>();
			for (Set<String> v : m.values()) {
				ret.addAll(v);
			}
			return ret;
		}
	}
	
	private String fileName;
	
	public ParsePolicyFromJSON(String fileName) {
		this.fileName = fileName;
	}

	public IFCPolicy run() throws IOException, JSONException {
		File policyFile = new File(fileName);
		String s = readFromFile(policyFile);
		JSONObject p = new JSONObject(s);
		JSONObject uASG = p.getJSONObject("uriArgumentSensitiveGetters");
		JSONObject sASG = p.getJSONObject("stringArgumentSensitiveGetters");
		JSONObject sources = p.getJSONObject("sources");
		JSONObject sinks = p.getJSONObject("sinks");
		List<Source> lstSources = getArgumentSensitiveSources(uASG);
		lstSources.addAll(getArgumentSensitiveSources(sASG));
		lstSources.addAll(getSources(sources));
		List<Sink> lstSinks = getSinks(sinks);
		ForbiddenThings f = scanForForbiddenThings(sources);
		return new IFCPolicy(lstSources, lstSinks, f);
	}
	
	private List<String> getCategories(JSONObject srcsOrSnks) {
		Iterator<String> iter = srcsOrSnks.keys();
		List<String> ret = new LinkedList<String>();
		while (iter.hasNext()) {
			ret.add(iter.next());
		}
		return ret;
	}
	
	private List<Source> getArgumentSensitiveSources(JSONObject aSG) throws JSONException {
		// uASG is a map className -> (methodName -> Array<ParameterIndex>)
		Iterator<String> classNames = aSG.keys();
		List<Source> ret = new LinkedList<Source>();
		while (classNames.hasNext()) {
			String nextClassName = classNames.next();
			JSONObject cls2MP = aSG.getJSONObject(nextClassName);
			ret.addAll(getArgumentSensitiveSourcesForClass(cls2MP, nextClassName));
		}
		return ret;
	}
	
	private List<Source> getArgumentSensitiveSourcesForClass(JSONObject aSG4C, String className) throws JSONException {
		// aSG is a map methodName -> Array<ParameterIndex>
		Iterator<String> methodNames = aSG4C.keys();
		List<Source> ret = new LinkedList<Source>();
		while (methodNames.hasNext()) {
			String nextMethodName = methodNames.next();
			JSONArray params = aSG4C.getJSONArray(nextMethodName);
			Source nextSource = new Source();
			nextSource.category = SourceOrSink.UNKNOWN_CATEGORY;
			nextSource.declaringClass = className;
			nextSource.method = nextMethodName;
			nextSource.params = new int[1];
			nextSource.params[0] = SourceOrSink.RET_PARAM;
			for (int i = 0; i < params.length(); i++) {
				int p = params.getInt(i);
				nextSource.sensitiveOn.add(p);
			}
			ret.add(nextSource);
		}
		return ret;
	}
	
	private List<Source> getSources(JSONObject sources) throws JSONException {
		// sources contains a map categoryName -> (className --"getters"--> Array<Method>) 
		Iterator<String> categoryNames = sources.keys();
		List<Source> ret = new LinkedList<Source>();
		while (categoryNames.hasNext()) {
			String nextCatName = categoryNames.next();
			ret.addAll(getSourcesForCategory(nextCatName, sources.getJSONObject(nextCatName)));
		}
		return ret;
	}
	
	private List<Source> getSourcesForCategory(String category, JSONObject srcsForCat) throws JSONException {
		// srcsForCat contains a map className --"getters"--> Array<Method>
		Iterator<String> classNames = srcsForCat.keys();
		List<Source> ret = new LinkedList<Source>();
		while (classNames.hasNext()) {
			String nextClass = classNames.next();
			JSONObject forClass = srcsForCat.getJSONObject(nextClass);
			if (forClass.has("getters")) {
				ret.addAll(getSourcesForClass(category, nextClass, forClass.getJSONArray("getters")));
			}
			if (forClass.has("entryPoints")) {
				ret.addAll(getEPFormalSourcesForClass(category, nextClass, forClass.getJSONObject("entryPoints")));
			}
		}
		return ret;
	}
	
	private List<Source> getSourcesForClass(String category, String className, JSONArray getters) throws JSONException {
		// getters is Array<Method>
		List<Source> ret = new LinkedList<Source>();
		for (int i = 0; i < getters.length(); i++) {
			Source src = new Source();
			src.category = category;
			src.declaringClass = className;
			src.method = getters.getString(i);
			src.params = new int[1];
			src.params[0] = SourceOrSink.RET_PARAM;
			ret.add(src);
		}
		return ret;
	}

	private List<Source> getEPFormalSourcesForClass(String category, String className, JSONObject entryPoints) throws JSONException {
		// getters is Array<Method
		List<Source> ret = new LinkedList<Source>();
		for (String epName : keySet(entryPoints)) {
			String secLevelsOfParams = entryPoints.getString(epName);
			for (int i = 0; i < secLevelsOfParams.length(); i++) {
				int highParam = 0;
				if (secLevelsOfParams.charAt(i) == 'h') {
					Source src = new Source();
					src.isEntryPointSource = true;
					src.category = category;
					src.declaringClass = className;
					src.method = epName;
					src.params = new int[1];
					src.params[highParam] = i + 1;
					highParam++;
					ret.add(src);
				}
			}
		}
		return ret;
	}

	private List<Sink> getSinks(JSONObject sinks) throws JSONException {
		// sinks contains a map categoryName -> (className --"setters"--> Array<Method>) 
		Iterator<String> categoryNames = sinks.keys();
		List<Sink> ret = new LinkedList<Sink>();
		while (categoryNames.hasNext()) {
			String nextCatName = categoryNames.next();
			ret.addAll(getSinksForCategory(nextCatName, sinks.getJSONObject(nextCatName)));
		}
		return ret;
	}
	
	private List<Sink> getSinksForCategory(String category, JSONObject snksForCat) throws JSONException {
		// srcsForCat contains a map className --"getters"--> Array<Method>
		Iterator<String> classNames = snksForCat.keys();
		List<Sink> ret = new LinkedList<Sink>();
		while (classNames.hasNext()) {
			String nextClass = classNames.next();
			JSONObject forClass = snksForCat.getJSONObject(nextClass);
			if (forClass.has("setters")) {
				ret.addAll(getSinksForClass(category, nextClass, forClass.getJSONArray("setters")));
			}
		}
		return ret;
	}
	
	private ForbiddenThings scanForForbiddenThings(JSONObject sources) throws JSONException {
		// category --> className -"forbiddenStrings"-> Array<string>
		// or
		// category --> className -"forbiddenUris"-> Array<string>
		ForbiddenThings ret = new ForbiddenThings();
		for (String category : keySet(sources)) {
			JSONObject forCat = sources.getJSONObject(category);
			for (String className : keySet(sources.getJSONObject(category))) {
				JSONObject forClass = forCat.getJSONObject(className);
				if (forClass.has("forbiddenStrings")) {
					JSONArray forbiddenStrings = forClass.getJSONArray("forbiddenStrings");
					ret.addStrings(category, arrayItems(forbiddenStrings));
				}
				if (forClass.has("forbiddenUris")) {
					JSONArray forbiddenUris = forClass.getJSONArray("forbiddenUris");
					ret.addUris(category, arrayItems(forbiddenUris));
				}
			}
		}
		return ret;
	}
	
	private static Set<String> keySet(JSONObject o) {
		Iterator<String> i = o.keys();
		Set<String> ret = new HashSet<String>();
		while (i.hasNext()) {
			String next = i.next();
			ret.add(next);
		}
		return ret;
	}
	
	private static Set<String> arrayItems(JSONArray a) throws JSONException {
		Set<String> ret = new HashSet<String>();
		int N = a.length();
		for (int i = 0; i < N; i++) {
			ret.add(a.getString(i));
		}
		return ret;
	}
	
	private List<Sink> getSinksForClass(String category, String className, JSONArray setters) throws JSONException {
		// getters is Array<Method>
		List<Sink> ret = new LinkedList<Sink>();
		for (int i = 0; i < setters.length(); i++) {
			Sink snk = new Sink();
			snk.category = category;
			snk.declaringClass = className;
			snk.method = setters.getString(i);
			snk.params = new int[1];
			snk.params[0] = SourceOrSink.ALL_ARGUMENTS;
			ret.add(snk);
		}
		return ret;
	}

	private String readFromFile(File policyFile) throws IOException {
		FileInputStream fis = new FileInputStream(policyFile);
		BufferedReader bis = new BufferedReader(new InputStreamReader(fis));
		StringBuilder sb = new StringBuilder();
		String nextLine = bis.readLine();
		while (nextLine != null) {
			sb.append(nextLine);
			nextLine = bis.readLine();
		}
		bis.close();
		return sb.toString();
	}
}
