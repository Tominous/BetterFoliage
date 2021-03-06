package mods.betterfoliage.client.integration

import mods.betterfoliage.client.Client
import mods.betterfoliage.loader.Refs
import mods.octarinecore.ThreadLocalDelegate
import mods.octarinecore.client.render.BlockContext
import mods.octarinecore.common.Int3
import mods.octarinecore.metaprog.allAvailable
import mods.octarinecore.metaprog.reflectField
import net.minecraft.block.state.BlockStateBase
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.apache.logging.log4j.Level.INFO

/**
 * Integration for OptiFine.
 */
@Suppress("UNCHECKED_CAST")
@SideOnly(Side.CLIENT)
object OptifineCTM {

    val isCTMAvailable = allAvailable(
        Refs.ConnectedTextures, Refs.ConnectedProperties,
        Refs.getConnectedTexture,
        Refs.CTblockProperties, Refs.CTtileProperties,
        Refs.CPtileIcons, Refs.CPmatchesIcon
    )
    val isColorAvailable = allAvailable(
        Refs.CustomColors, Refs.getColorMultiplier
    )

    init {
        Client.log(INFO, "Optifine CTM support is ${if (isCTMAvailable) "enabled" else "disabled" }")
        Client.log(INFO, "Optifine custom color support is ${if (isColorAvailable) "enabled" else "disabled" }")
    }

    val renderEnv by ThreadLocalDelegate { OptifineRenderEnv() }
    val fakeQuad = BakedQuad(IntArray(0), 1, EnumFacing.UP, null, true, DefaultVertexFormats.BLOCK)
    val isCustomColors: Boolean get() = if (!isCTMAvailable) false else Minecraft.getMinecraft().gameSettings.reflectField<Boolean>("ofCustomColors") ?: false

    val connectedProperties: Iterable<Any> get() {
        val result = hashSetOf<Any>()
        (Refs.CTblockProperties.getStatic() as Array<Array<Any?>?>?)?.forEach { cpArray ->
            cpArray?.forEach { if (it != null) result.add(it) }
        }
        (Refs.CTtileProperties.getStatic() as Array<Array<Any?>?>?)?.forEach { cpArray ->
            cpArray?.forEach { if (it != null) result.add(it) }
        }
        return result
    }

    /** Get all the CTM [TextureAtlasSprite]s that could possibly be used for this block. */
    fun getAllCTM(state: IBlockState, icon: TextureAtlasSprite): Collection<TextureAtlasSprite> {
        val result = hashSetOf<TextureAtlasSprite>()
        if (state !is BlockStateBase || !isCTMAvailable) return result

        connectedProperties.forEach { cp ->
            if (Refs.CPmatchesBlock.invoke(cp, Refs.getBlockId.invoke(state), Refs.getMetadata.invoke(state)) as Boolean &&
                Refs.CPmatchesIcon.invoke(cp, icon) as Boolean) {
                Client.log(INFO, "Match for block: ${state.toString()}, icon: ${icon.iconName} -> CP: ${cp.toString()}")
                result.addAll(Refs.CPtileIcons.get(cp) as Array<TextureAtlasSprite>)
            }
        }
        return result
    }

    fun getAllCTM(states: List<IBlockState>, icon: TextureAtlasSprite): Collection<TextureAtlasSprite> =
        states.flatMap { getAllCTM(it, icon) }.toSet()

    fun override(texture: TextureAtlasSprite, ctx: BlockContext, face: EnumFacing) =
        override(texture, ctx.world!!, ctx.pos, face)

    fun override(texture: TextureAtlasSprite, world: IBlockAccess, pos: BlockPos, face: EnumFacing): TextureAtlasSprite {
        if (!isCTMAvailable) return texture
        val state = world.getBlockState(pos)

        return renderEnv.let {
            it.reset(world, state, pos)
            Refs.getConnectedTexture.invokeStatic(world, state, pos, face, texture, it.wrapped) as TextureAtlasSprite
        }
    }

    fun getBlockColor(ctx: BlockContext): Int {
        val ofColor = if (isColorAvailable && Minecraft.getMinecraft().gameSettings.reflectField<Boolean>("ofCustomColors") == true) {
            renderEnv.reset(ctx.world!!, ctx.blockState(Int3.zero), ctx.pos)
            Refs.getColorMultiplier.invokeStatic(fakeQuad, ctx.blockState(Int3.zero), ctx.world!!, ctx.pos, renderEnv.wrapped) as? Int
        } else null
        return ofColor ?: ctx.blockData(Int3.zero).color
    }
}

@SideOnly(Side.CLIENT)
class OptifineRenderEnv {
    val wrapped: Any = Refs.RenderEnv.element!!.getDeclaredConstructor(
        Refs.IBlockAccess.element, Refs.IBlockState.element, Refs.BlockPos.element
    ).let {
        it.isAccessible = true
        it.newInstance(null, null, null)
    }

    fun reset(blockAccess: IBlockAccess, state: IBlockState, pos: BlockPos) {
        Refs.RenderEnv_reset.invoke(wrapped, blockAccess, state, pos)
    }
}