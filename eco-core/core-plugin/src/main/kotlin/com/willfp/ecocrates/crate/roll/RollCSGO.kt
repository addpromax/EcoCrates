package com.willfp.ecocrates.crate.roll

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.slot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.core.items.Items
import com.willfp.eco.util.NumberUtils
import com.willfp.ecocrates.crate.Crate
import com.willfp.ecocrates.reward.Reward
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class RollCSGO private constructor(
    override val reward: Reward,
    private val crate: Crate,
    private val plugin: EcoPlugin,
    private val player: Player
) : Roll {
    private val scrollTimes = plugin.configYml.getInt("rolls.csgo.scrolls")
    private val bias = plugin.configYml.getDouble("rolls.csgo.bias")
    private val maxDelay = plugin.configYml.getInt("rolls.csgo.max-delay")

    /*
    Bias the number using the NumberUtils bias function,
    but varying inputs from 0-35 rather than 0-1,
    and then multiplying the output by 25 to give
    a maximum tick value of 25 (rather than 1);
    essentially doing f(x/<scrolls>) * <max delay>.
     */
    private val delays = (1..scrollTimes)
        .asSequence()
        .map { it / scrollTimes.toDouble() }
        .map { NumberUtils.bias(it, bias) }
        .map { it * maxDelay }
        .map { it.toInt() }
        .map { if (it <= 0) 1 else it }  // Make it 1!
        .toList()

    // Add three so it lines up
    private val display = crate.getRandomRewards(scrollTimes + 3, displayWeight = true)
        .toMutableList().apply {
            add(reward)
            addAll(crate.getRandomRewards(5, displayWeight = true))
        }

    private var scroll = 0
    private var tick = 0

    private val runnable = plugin.runnableFactory.create {
        val currentDelay = delays[scroll]

        if (tick % currentDelay == 0) {
            tick = 0
            scroll++

            gui.refresh(player)

            player.playSound(
                player.location,
                Sound.BLOCK_STONE_BUTTON_CLICK_ON,
                1.0f,
                1.0f
            )

            if (scroll >= scrollTimes) {
                it.cancel()
                plugin.scheduler.runLater(60) { player.closeInventory() }
            }
        }

        tick++
    }

    private val gui = menu(3) {
        setMask(
            FillerMask(
                MaskItems(
                    Items.lookup(plugin.configYml.getString("rolls.csgo.filler")),
                    Items.lookup(plugin.configYml.getString("rolls.csgo.selector"))
                ),
                "111121111",
                "000000000",
                "111121111"
            )
        )

        setTitle(crate.name)

        for (i in 1..9) {
            setSlot(
                2,
                i,
                slot(
                    ItemStack(Material.AIR)
                ) {
                    setUpdater { _, _, _ ->
                        display[(9 - i) + scroll].display
                    }
                }
            )
        }

        onClose { event, _ ->
            handleFinish(event.player as Player)
        }
    }

    override fun roll() {
        gui.open(player)

        // Run the scroll animation
        runnable.runTaskTimer(1, 1)
    }

    private fun handleFinish(player: Player) {
        runnable.cancel()
        crate.handleFinish(player, this)
    }

    object Factory : RollFactory<RollCSGO>("csgo") {
        override fun create(options: RollOptions): RollCSGO =
            RollCSGO(
                options.reward,
                options.crate,
                options.plugin,
                options.player
            )
    }
}
