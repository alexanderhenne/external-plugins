/*
 * Copyright (c) 2020, Lotto <https://github.com/devLotto>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.monkeymetrics;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Monkey Metrics",
	description = "Attack info (hitsplat count, total dmg), NPC stack size and more."
)
public class MonkeyMetricsPlugin extends Plugin
{
	static final String CONFIG_KEY = "monkeymetrics";

	private static final Set<Skill> SKILLS_TO_TRACK = ImmutableSet.of(Skill.RANGED, Skill.MAGIC);

	private static final Set<String> allowedNpcNames = ImmutableSet.of(
		"Maniacal monkey",
		"Skeleton",
		"Dust devil",
		"Abyssal demon",
		"Greater abyssal demon",
		"Greater Nechryael",
		"Nechryarch",
		"Smoke devil",
		"Choke devil",
		"Nuclear smoke devil",
		"Warped Jelly",
		"Vitreous warped Jelly",
		"Ankou",
		"Dagannoth",
		"TzHaar-Hur",
		"TzHaar-Mej",
		"TzHaar-Ket",
		"TzHaar-Xil"
	);

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private AttackMetricsOverlay metricsOverlay;

	@Inject
	private NpcStacksOverlay stacksOverlay;

	@Inject
	private MonkeyMetricsConfig config;

	private AttackMetrics metrics = new AttackMetrics();
	private Map<Skill, Integer> cachedExp = new HashMap<>();

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(metricsOverlay);
		overlayManager.add(stacksOverlay);

		reset();
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(metricsOverlay);
		overlayManager.remove(stacksOverlay);

		reset();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!config.showMetrics())
		{
			return;
		}

		final Actor actor = event.getActor();

		if (!(actor instanceof NPC))
		{
			return;
		}

		final Hitsplat hitsplat = event.getHitsplat();

		if (hitsplat.getHitsplatType() != Hitsplat.HitsplatType.DAMAGE_ME
			&& hitsplat.getHitsplatType() != Hitsplat.HitsplatType.BLOCK_ME)
		{
			return;
		}

		metrics.setHitsplats(metrics.getHitsplats() + 1);
		metrics.setDamage(metrics.getDamage() + hitsplat.getAmount());
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (config.showNpcStacks())
		{
			updateNpcStacks();
		}

		if (config.showMetrics())
		{
			updateMetrics();
		}
	}

	private void updateNpcStacks()
	{
		final Map<LocalPoint, Integer> npcStacks = new HashMap<>();

		for (NPC npc : client.getNpcs())
		{
			final NPCComposition composition = npc.getTransformedComposition();
			if (composition == null)
				continue;

			final String name = composition.getName();
			if (!allowedNpcNames.contains(name))
				continue;

			final LocalPoint location = LocalPoint.fromWorld(client, npc.getWorldLocation());

			npcStacks.put(location, npcStacks.getOrDefault(location, 0) + 1);
		}

		npcStacks.entrySet().removeIf(e -> e.getValue() < config.minimumNpcStackSize());

		stacksOverlay.setNpcStacks(npcStacks);
	}

	private void updateMetrics()
	{
		// Only update metrics overlay if we've attacked a target.
		if (metrics.getHitsplats() == 0)
		{
			return;
		}

		final AttackMetrics oldMetrics = this.metrics;

		metricsOverlay.setMetrics(oldMetrics);

		// Reset for the next tick.
		metrics = new AttackMetrics();

		// However, remember skills trained during previous ticks.
		oldMetrics.getGainedExp().forEach((skill, exp) -> metrics.getGainedExp().put(skill, 0));
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (!config.showMetrics())
		{
			return;
		}

		final Skill skill = event.getSkill();

		if (!SKILLS_TO_TRACK.contains(skill))
		{
			return;
		}

		final int currentExp = event.getXp();

		if (cachedExp.containsKey(skill))
		{
			final int lastExp = cachedExp.get(skill);
			final int expDelta = Math.max(0, currentExp - lastExp);

			metrics.getGainedExp().put(skill, metrics.getGainedExp().getOrDefault(skill, 0) + expDelta);
		}

		cachedExp.put(skill, currentExp);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		final GameState state = event.getGameState();

		if (state == GameState.LOGGING_IN || state == GameState.HOPPING)
		{
			reset();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(CONFIG_KEY))
		{
			reset();
		}
	}

	private void reset()
	{
		stacksOverlay.setNpcStacks(null);

		metrics = new AttackMetrics();
		cachedExp.clear();
		metricsOverlay.setMetrics(null);

		if (client.getLocalPlayer() != null)
		{
			for (Skill skill : SKILLS_TO_TRACK)
			{
				cachedExp.put(skill, client.getSkillExperience(skill));
			}
		}
	}

	@Provides
	MonkeyMetricsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MonkeyMetricsConfig.class);
	}
}
