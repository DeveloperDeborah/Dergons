package no.runsafe.dergons;

import no.runsafe.dergons.commands.PurgeDergons;
import no.runsafe.dergons.commands.SpawnDergon;
import no.runsafe.dergons.recipes.DergonboneHelmet;
import no.runsafe.framework.RunsafeConfigurablePlugin;
import no.runsafe.framework.api.command.Command;
import no.runsafe.framework.features.Commands;
import no.runsafe.framework.features.Events;

public class Dergons extends RunsafeConfigurablePlugin
{
	@Override
	protected void pluginSetup()
	{
		// Framework features
		addComponent(Commands.class);
		addComponent(Events.class);

		// Plugin components
		addComponent(DergonHandler.class);
		addComponent(DergonSpawner.class);

		// Recipes
		addComponent(DergonboneHelmet.class);

		Command dergonCommand = new Command("dergons", "Dergon related commands", null);
		addComponent(dergonCommand);

		dergonCommand.addSubCommand(getInstance(PurgeDergons.class));
		dergonCommand.addSubCommand(getInstance(SpawnDergon.class));
	}
}
