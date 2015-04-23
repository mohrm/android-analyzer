package edu.kit.jodroid.policy;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.kit.jodroid.io.ParsePolicyFromJSON;
import edu.kit.jodroid.io.ParsePolicyFromJSON.ForbiddenThings;
import edu.kit.jodroid.io.ParsePolicyFromJSON.Sink;
import edu.kit.jodroid.io.ParsePolicyFromJSON.Source;
import edu.kit.jodroid.io.ParsePolicyFromJSON.SourceOrSink;

public class IFCPolicy {

	private List<Source> sources;
	private List<Sink> sinks;
	private ForbiddenThings forbidden;

	public IFCPolicy(List<Source> sources, List<Sink> sinks, ForbiddenThings forbidden) {
		this.sources = resolveUnknownSources(sources);
		this.sinks = resolveUnknownSinks(sinks);
		this.forbidden = forbidden;
	}
	
	private static List<Source> resolveUnknownSources(List<Source> srcs) {
		final List<Source> ret = new LinkedList<Source>();
		List<Source> unknown = collectUnknown(srcs);
		Set<String> knownCategories = collectKnownCategories(srcs);
		ret.addAll(collectKnown(srcs));
		for (Source usrc : unknown) {
			for (final String cat : knownCategories) {
				ret.add(usrc.withCategory(cat));
			}
		}
		return ret;
	}
	
	private static List<Sink> resolveUnknownSinks(List<Sink> snks) {
		final List<Sink> ret = new LinkedList<Sink>();
		List<Sink> unknown = collectUnknown(snks);
		Set<String> knownCategories = collectKnownCategories(snks);
		ret.addAll(collectKnown(snks));
		for (Sink usnk : unknown) {
			for (final String cat : knownCategories) {
				ret.add(usnk.withCategory(cat));
			}
		}
		return ret;
	}

	private static <T extends SourceOrSink> List<T> collectUnknown(List<T> pois) {
		List<T> ret = new LinkedList<T>();
		for (T p : pois) {
			if (p.getCategory().equals(ParsePolicyFromJSON.SourceOrSink.UNKNOWN_CATEGORY)) {
				ret.add(p);
			}
		}
		return ret;
	}
	
	private static <T extends SourceOrSink> List<T> collectKnown(List<T> pois) {
		List<T> ret = new LinkedList<T>();
		for (T p : pois) {
			if (!p.getCategory().equals(ParsePolicyFromJSON.SourceOrSink.UNKNOWN_CATEGORY)) {
				ret.add(p);
			}
		}
		return ret;
	}
	
	private static Set<String> collectKnownCategories(List<? extends SourceOrSink> pois) {
		Set<String> ret = new HashSet<String>();
		for (SourceOrSink p : pois) {
			if (!p.getCategory().equals(ParsePolicyFromJSON.SourceOrSink.UNKNOWN_CATEGORY)) {
				ret.add(p.getCategory());
			}
		}
		return ret;
	}
	
	public List<Source> getSources() {
		return sources;
	}
	
	public List<Sink> getSinks() {
		return sinks;
	}
	
	public List<SourceOrSink> getSourcesAndSinks() {
		List<SourceOrSink> ret = new LinkedList<SourceOrSink>();
		ret.addAll(getSources());
		ret.addAll(getSinks());
		return ret;
	}
	
	public ForbiddenThings getForbidden() {
		return forbidden;
	}
}
