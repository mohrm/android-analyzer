package edu.kit.jodroid.io.test;

import java.io.IOException;

import org.json.JSONException;

import edu.kit.jodroid.io.ParsePolicyFromJSON;

public class ParsePolicyFromJSONTest {
	public static void main(String[] args) throws IOException, JSONException {
		new ParsePolicyFromJSON("res/policytemplate.json").run();
	}
}
