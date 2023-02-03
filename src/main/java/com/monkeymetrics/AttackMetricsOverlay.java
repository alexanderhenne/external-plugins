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

import com.google.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import lombok.AccessLevel;
import lombok.Setter;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class AttackMetricsOverlay extends Overlay
{
	private final MonkeyMetricsConfig config;

	private final PanelComponent panelComponent = new PanelComponent();

	@Setter(AccessLevel.PACKAGE)
	private AttackMetrics metrics;
	private final MonkeyMetricsPlugin plugin;

	@Inject
	AttackMetricsOverlay(MonkeyMetricsPlugin monkeyMetricsPlugin, MonkeyMetricsConfig config)
	{
		super(monkeyMetricsPlugin);
		this.plugin = monkeyMetricsPlugin;
		this.config = config;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// If Metrics overlay disabled,
		// metrics are null and timeout is enabled
		// or metrics are marked inactive.
		if (!config.showMetrics()
			|| (metrics == null && config.overlayTimeout() != 0)
			|| !metrics.isActive())
		{
			return null;
		}

		panelComponent.getChildren().clear();

		panelComponent.getChildren().add(
			TitleComponent.builder()
				.text("Previous attack")
				.build());

		if (metrics != null)
		{
			panelComponent.getChildren().add(
				LineComponent.builder()
					.left("Hitsplats")
					.right(String.valueOf(metrics.getHitsplats()))
					.build());

			panelComponent.getChildren().add(
				LineComponent.builder()
					.left("Total damage")
					.right(metrics.getDamage() + " hp")
					.build());

			metrics.getGainedExp().forEach(this::appendActiveStyle);
		}
		else
		{
			panelComponent.getChildren().add(
				LineComponent.builder()
					.left("Monkey Metrics plugin waiting for NPC damage..")
					.build());
		}

		return panelComponent.render(graphics);
	}

	private void appendActiveStyle(Skill skill, Integer exp)
	{
		int timeout = config.attackStyleTimeout();
		// Check if the setting is enabled, and check if no XP was gained last tick
		if (timeout != 0 && exp == 0)
		{
			// Get how many game ticks since last attack
			int ticks = plugin.getTicksSinceExpDrop().getOrDefault(skill, -1);
			// If skill has not been tracked, or ticks since last attack >= timeout
			if (ticks == -1 || ticks >= timeout)
			{
				return;
			}
		}

		panelComponent.getChildren().add(
			LineComponent.builder()
				.left(skill.getName())
				.right("+" + exp + " xp")
				.build());
	}
}
