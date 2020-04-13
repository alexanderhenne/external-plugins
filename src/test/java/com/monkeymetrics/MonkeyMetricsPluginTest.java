package com.monkeymetrics;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MonkeyMetricsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MonkeyMetricsPlugin.class);
		RuneLite.main(args);
	}
}