package com.branders.spawnermod.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Random;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemSpawnEgg;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.MobSpawnerBaseLogic;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * 	Handles all the events regarding the mob spawner and entities.
 * 
 * 	@author Branders
 *
 */

@EventBusSubscriber
public class SpawnerEventHandler
{
	private float SPAWN_RATE = 0.04F;
	private Random random = new Random();
	
	private EntityType<?> defaultEntityType = EntityType.AREA_EFFECT_CLOUD;
	
	/**
     * 	When we harvest a block
     * 	Return spawner block when harvested with silk touch
     */
    @SubscribeEvent
    public void onBlockHarvestDrops(BlockEvent.HarvestDropsEvent event)
    {
    	if(event.getState().getBlock() == Blocks.SPAWNER)
    	{   
    		NBTTagList list = event.getHarvester().getHeldItemMainhand().getEnchantmentTagList();
    		
    		// Check if silk touch enchant is on the tool
    		if(CheckSilkTouch(list))
    			event.getDrops().add(new ItemStack(Blocks.SPAWNER, 1));
    	}
    }
    
    /**
     * 	When a block is destroyed
     * 	Prevent XP drop when spawner is destroyed with silk touch
     */
    @SubscribeEvent
    public void onBlockBreakEvent(BlockEvent.BreakEvent event) 
    {	
    	// Check if a spawner broke
    	if(event.getState().getBlock() == Blocks.SPAWNER)
    	{
    		NBTTagList list = event.getPlayer().getHeldItemMainhand().getEnchantmentTagList();
    		
    		// Return 0 EXP when harvested with silk touch
    		if(CheckSilkTouch(list))
    			event.setExpToDrop(0);
    	}
    }
    
    
    /**
     * 	Called when a block gets an update
     * 	Used to replace entity in spawner when block placed
     */
    @SubscribeEvent
    public void onNotifyEvent(BlockEvent.NeighborNotifyEvent event)
    {
    	if(event.getState().getBlock() != Blocks.SPAWNER)
    		return;
    	
    	World world = (World)event.getWorld();
    	
    	BlockPos blockpos = event.getPos();
    	IBlockState iblockstate = world.getBlockState(blockpos);

    	TileEntityMobSpawner spawner = (TileEntityMobSpawner)world.getTileEntity(blockpos);
    	MobSpawnerBaseLogic logic = spawner.getSpawnerBaseLogic();

    	// Replace the entity inside the spawner with default entity
    	logic.setEntityType(defaultEntityType);
    	spawner.markDirty();
    	world.notifyBlockUpdate(blockpos, iblockstate, iblockstate, 3);
    }
    
    
    /**
     * 	Called when a mob drops items
     * 	Enables mobs to have a small chance to drop an egg
     */
    @SubscribeEvent
    public void onMobDrop(LivingDropsEvent event)
    {	
    	if(random.nextFloat() > SPAWN_RATE)
    		return;
    	
    	Entity entity = event.getEntity();
    	EntityType<?> entityType = entity.getType();
    	
		// Get the entity mob egg and put in an ItemStack
		ItemSpawnEgg egg = ItemSpawnEgg.getEgg(entityType);
		ItemStack itemStack = new ItemStack(egg);
		
		// Add egg in drops
		event.getDrops().add(new EntityItem(entity.world, entity.posX, entity.posY, entity.posZ, itemStack));
    }
    
    
    /**
     * 	Event when player interacts with block
     * 	Enables so that the player can right click a spawner to get its egg
     */
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.RightClickBlock event)
    {
    	World world = event.getWorld();
    	
    	// Leave if we are client and if the block isn't a spawner
    	if(world.isRemote || event.getItemStack().getItem() instanceof ItemBlock)		
    		return;
    	
    	BlockPos blockpos = event.getPos();
		IBlockState iblockstate = world.getBlockState(blockpos);	
		
    	// Check if we right-clicked
		if(world.getBlockState(blockpos).getBlock() == Blocks.SPAWNER && event.getHand() == EnumHand.MAIN_HAND)
    	{	
			// Return mob egg
			try {
				DropMonsterEgg(world, blockpos, iblockstate);
			} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    }
    
    /**
     * 	Spawns a mob egg depending on what type of entity inside mob spawner.
     * 	When successfully retrieved monster egg we set spawner entity to default.
     * 
     * 	Uses Java reflection to get entity type inside spawner
	 * 
     * 	@throws SecurityException 
     * 	@throws NoSuchMethodException 
     * 	@throws InvocationTargetException 
     * 	@throws IllegalArgumentException 
     * 	@throws IllegalAccessException
     */
    private void DropMonsterEgg(World world, BlockPos blockpos, IBlockState iblockstate) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException
    {
    	TileEntityMobSpawner spawner = (TileEntityMobSpawner)world.getTileEntity(blockpos);
    	
    	if(spawner == null)
    		System.out.println("Spawner is null");
    	
    	MobSpawnerBaseLogic logic = spawner.getSpawnerBaseLogic();
		    	
		// Use Java Reflection to call private method getEntityId in MobSpawnerBaseLogic class
		Method method = logic.getClass().getSuperclass().getDeclaredMethod("getEntityId");
		method.setAccessible(true);
		ResourceLocation resource = (ResourceLocation)method.invoke(logic);

		// Get entity
		EntityType<?> entityType = EntityType.getById(resource.toString());
		
		// Leave if the spawner does not contain an egg
		if(entityType.equals(defaultEntityType))
			return;
		
    	// Get the entity mob egg and put in an ItemStack
		ItemSpawnEgg egg = ItemSpawnEgg.getEgg(entityType);
		ItemStack itemStack = new ItemStack(egg);
		
		// Get random fly-out position offsets
		double d0 = (double)(world.rand.nextFloat() * 0.7F) + (double)0.15F;
        double d1 = (double)(world.rand.nextFloat() * 0.7F) + (double)0.06F + 0.6D;
        double d2 = (double)(world.rand.nextFloat() * 0.7F) + (double)0.15F;
        
        // Create entity item
		EntityItem entityItem = new EntityItem(world, (double)blockpos.getX() + d0, (double)blockpos.getY() + d1, (double)blockpos.getZ() + d2, itemStack);
		entityItem.setDefaultPickupDelay();
		
		// Spawn entity item (egg)
		world.spawnEntity(entityItem);
		
		// Replace the entity inside the spawner with default entity
		logic.setEntityType(defaultEntityType);
		spawner.markDirty();
		world.notifyBlockUpdate(blockpos, iblockstate, iblockstate, 3);
    }
    
    /**
     * 	Check a tools item enchantment list contains Silk Touch enchant
     * 	I don't know if there's a better way to do this	
     * 
     * 	@param NBTTagList of enchantment
     * 	@return true/false
     */
    private boolean CheckSilkTouch(NBTTagList list)
    {
    	// Check list string contains silk touch
		if(list.getString().indexOf("minecraft:silk_touch") != -1)
			return true;
		else
			return false;
    }
}
