package org.dimdev.ddutils.schem;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.ArrayListMultimap;
import cubicchunks.world.ICubicWorld;
import cubicchunks.world.cube.Cube;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.dimdev.dimdoors.DimDoors;

import java.util.*;
import java.util.Map.Entry;

/**
 * @author Robijnvogel
 */
public class Schematic {

    private static final boolean cubicChunks = Loader.isModLoaded("cubicchunks");

    public int version = 1;
    public String author = null;
    public String name = null;
    public long creationDate;
    public String[] requiredMods = {};
    public short width;
    public short height;
    public short length;
    public int[] offset = {0, 0, 0};
    public int paletteMax;
    public List<IBlockState> palette = new ArrayList<>();
    public short[][][] blockData; //[x][y][z]
    public List<NBTTagCompound> tileEntities = new ArrayList<>();
    public List<NBTTagCompound> entities = new ArrayList<>(); // Not in the specification, but we need this

    public Schematic() {
        paletteMax = -1;
    }

    public Schematic(short width, short height, short length) {
        this();
        this.width = width;
        this.height = height;
        this.length = length;
        blockData = new short[width][length][height];
        palette.add(Blocks.AIR.getDefaultState());
        paletteMax++;
        creationDate = System.currentTimeMillis();
    }

    public Schematic(String name, String author, short width, short height, short length) {
        this(width, height, length);
        this.name = name;
        this.author = author;
    }

    public static Schematic loadFromNBT(NBTTagCompound nbt) {
        Schematic schematic = new Schematic();
        schematic.version = nbt.getInteger("Version"); //Version is required

        schematic.creationDate = System.currentTimeMillis();
        if (nbt.hasKey("Metadata")) { //Metadata is not required
            NBTTagCompound metadataCompound = nbt.getCompoundTag("Metadata").getCompoundTag(".");
            if (nbt.hasKey("Author")) { //Author is not required
                schematic.author = metadataCompound.getString("Author");
            }
            //Name is not required (may be null)
            schematic.name = metadataCompound.getString("Name");

            if (nbt.hasKey("Date")) { //Date is not required
                schematic.creationDate = metadataCompound.getLong("Date");
            }
            if (nbt.hasKey("RequiredMods")) { //RequiredMods is not required (ironically)
                NBTTagList requiredModsTagList = (NBTTagList) metadataCompound.getTag("RequiredMods");
                schematic.requiredMods = new String[requiredModsTagList.tagCount()];
                for (int i = 0; i < requiredModsTagList.tagCount(); i++) {
                    schematic.requiredMods[i] = requiredModsTagList.getStringTagAt(i);
                }
            }
        }

        schematic.width = nbt.getShort("Width"); //Width is required
        schematic.height = nbt.getShort("Height"); //Height is required
        schematic.length = nbt.getShort("Length"); //Length is required
        if (nbt.hasKey("Offset")) { //Offset is not required
            schematic.offset = nbt.getIntArray("Offset");
        }

        NBTTagCompound paletteNBT = nbt.getCompoundTag("Palette"); //Palette is not required, however since we assume that the schematic contains at least some blocks, we can also assume that thee has to be a Palette
        Map<Integer, String> paletteMap = new HashMap<>();
        for (String key : paletteNBT.getKeySet()) {
            int paletteID = paletteNBT.getInteger(key);
            paletteMap.put(paletteID, key); //basically use the reversed order (key becomes value and value becomes key)
        }
        for (int i = 0; i < paletteMap.size(); i++) {
            String blockStateString = paletteMap.get(i);
            char lastBlockStateStringChar = blockStateString.charAt(blockStateString.length() - 1);
            String blockString;
            String stateString;
            if (lastBlockStateStringChar == ']') {
                String[] blockAndStateStrings = blockStateString.split("\\[");
                blockString = blockAndStateStrings[0];
                stateString = blockAndStateStrings[1];
                stateString = stateString.substring(0, stateString.length() - 1); //remove the "]" at the end
            } else {
                blockString = blockStateString;
                stateString = "";
            }
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockString));

            IBlockState blockstate = block.getDefaultState();
            if (!stateString.equals("")) {
                String[] properties = stateString.split(",");
                blockstate = getBlockStateWithProperties(block, properties);
            }
            schematic.palette.add(blockstate); //@todo, can we assume that a schematic file always has all palette integers used from 0 to pallettemax-1?
        }
        if (nbt.hasKey("PaletteMax")) { //PaletteMax is not required
            schematic.paletteMax = nbt.getInteger("PaletteMax");
        } else {
            schematic.paletteMax = schematic.palette.size() - 1;
        }

        byte[] blockDataIntArray = nbt.getByteArray("BlockData"); //BlockData is required
        schematic.blockData = new short[schematic.width][schematic.height][schematic.length];
        for (int x = 0; x < schematic.width; x++) {
            for (int y = 0; y < schematic.height; y++) {
                for (int z = 0; z < schematic.length; z++) {
                    schematic.blockData[x][y][z] = blockDataIntArray[x + z * schematic.width + y * schematic.width * schematic.length]; //according to the documentation on https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-1.md
                }
            }
        }

        if (nbt.hasKey("TileEntities")) { //TileEntities is not required
            NBTTagList tileEntitiesTagList = (NBTTagList) nbt.getTag("TileEntities");
            for (int i = 0; i < tileEntitiesTagList.tagCount(); i++) {
                NBTTagCompound tileEntityTagCompound = tileEntitiesTagList.getCompoundTagAt(i);
                schematic.tileEntities.add(tileEntityTagCompound);
            }
        }

        if (nbt.hasKey("Entities")) { //Entities is not required
            NBTTagList entitiesTagList = (NBTTagList) nbt.getTag("Entities");
            for (int i = 0; i < entitiesTagList.tagCount(); i++) {
                NBTTagCompound entityTagCompound = entitiesTagList.getCompoundTagAt(i);
                schematic.entities.add(entityTagCompound);
            }
        }

        return schematic;
    }

    public static NBTTagCompound saveToNBT(Schematic schematic) {
        NBTTagCompound nbt = new NBTTagCompound();

        nbt.setInteger("Version", schematic.version);
        NBTTagCompound metadataCompound = new NBTTagCompound();
        if (schematic.author != null) metadataCompound.setString("Author", schematic.author); // Author is not required
        metadataCompound.setString("Name", schematic.name);
        metadataCompound.setLong("Date", schematic.creationDate);
        NBTTagList requiredModsTagList = new NBTTagList();
        for (String requiredMod : schematic.requiredMods) {
            requiredModsTagList.appendTag(new NBTTagString(requiredMod));
        }
        metadataCompound.setTag("RequiredMods", requiredModsTagList);
        nbt.setTag("Metadata", metadataCompound);

        nbt.setShort("Width", schematic.width);
        nbt.setShort("Height", schematic.height);
        nbt.setShort("Length", schematic.length);
        nbt.setIntArray("Offset", schematic.offset);
        nbt.setInteger("PaletteMax", schematic.paletteMax);

        NBTTagCompound paletteNBT = new NBTTagCompound();
        for (int i = 0; i < schematic.palette.size(); i++) {
            IBlockState state = schematic.palette.get(i);
            String blockStateString = getBlockStateStringFromState(state);
            paletteNBT.setInteger(blockStateString, i);
        }
        nbt.setTag("Palette", paletteNBT);

        byte[] blockDataIntArray = new byte[schematic.width * schematic.height * schematic.length];
        for (int x = 0; x < schematic.width; x++) {
            for (int y = 0; y < schematic.height; y++) {
                for (int z = 0; z < schematic.length; z++) {
                    blockDataIntArray[x + z * schematic.width + y * schematic.width * schematic.length] = (byte) schematic.blockData[x][y][z]; //according to the documentation on https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-1.md
                }
            }
        }
        nbt.setByteArray("BlockData", blockDataIntArray);

        NBTTagList tileEntitiesTagList = new NBTTagList();
        for (int i = 0; i < schematic.tileEntities.size(); i++) {
            NBTTagCompound tileEntityTagCompound = schematic.tileEntities.get(i);
            tileEntitiesTagList.appendTag(tileEntityTagCompound);
        }
        nbt.setTag("TileEntities", tileEntitiesTagList);

        NBTTagList entitiesTagList = new NBTTagList();
        for (int i = 0; i < schematic.entities.size(); i++) {
            NBTTagCompound entityTagCompound = schematic.entities.get(i);
            entitiesTagList.appendTag(entityTagCompound);
        }
        nbt.setTag("Entities", entitiesTagList);

        return nbt;
    }

    static IBlockState getBlockStateWithProperties(Block block, String[] properties) {
        Map<String, String> propertyAndBlockStringsMap = new HashMap<>();
        for (String property : properties) {
            String[] propertyAndBlockStrings = property.split("=");
            propertyAndBlockStringsMap.put(propertyAndBlockStrings[0], propertyAndBlockStrings[1]);
        }
        BlockStateContainer container = block.getBlockState();
        IBlockState chosenState = block.getDefaultState();
        for (Entry<String, String> entry : propertyAndBlockStringsMap.entrySet()) {
            IProperty<?> property = container.getProperty(entry.getKey());
            if (property != null) {
                Comparable<?> value = null;
                for (Comparable<?> object : property.getAllowedValues()) {
                    if (object.toString().equals(entry.getValue())) {
                        value = object;
                        break;
                    }
                }
                if (value != null) {
                    chosenState = chosenState.withProperty((IProperty) property, (Comparable) value);
                }
            }
        }
        return chosenState;
    }

    private static String getBlockStateStringFromState(IBlockState state) {
        Block block = state.getBlock();
        String blockNameString = String.valueOf(Block.REGISTRY.getNameForObject(block));
        StringBuilder blockStateString = new StringBuilder();
        String totalString;
        IBlockState defaultState = block.getDefaultState();
        if (state == defaultState) {
            totalString = blockNameString;
        } else { //there is at least one property not equal to the default state's property
            BlockStateContainer container = block.getBlockState();
            for (IProperty<?> property : container.getProperties()) { //for every property that is valid for this type of Block
                String defaultPropertyValue = defaultState.getProperties().get(property).toString();
                String thisPropertyValue = state.getProperties().get(property).toString();
                if (!defaultPropertyValue.equals(thisPropertyValue)) {
                    String firstHalf = property.getName();
                    String secondHalf = state.getProperties().get(property).toString();
                    String propertyString = firstHalf + "=" + secondHalf;
                    blockStateString.append(propertyString).append(",");
                }
            }
            blockStateString = new StringBuilder(blockStateString.substring(0, blockStateString.length() - 1)); //removes the last comma
            totalString = blockNameString + "[" + blockStateString + "]";
        }
        return totalString;
    }

    // TODO: use the setBlockState method
    public static Schematic createFromWorld(World world, Vector3i from, Vector3i to) {
        Schematic schematic = new Schematic();

        Vector3i min = from.min(to);
        Vector3i max = from.max(to);
        Vector3i dimensions = max.sub(min).add(1, 1, 1);

        schematic.width = (short) dimensions.getX();
        schematic.height = (short) dimensions.getY();
        schematic.length = (short) dimensions.getZ();

        schematic.blockData = new short[schematic.width][schematic.height][schematic.length];

        ArrayListMultimap<IBlockState, BlockPos> states = ArrayListMultimap.create();
        Set<String> mods = new HashSet<>();

        for (int x = 0; x < dimensions.getX(); x++) {
            for (int y = 0; y < dimensions.getY(); y++) {
                for (int z = 0; z < dimensions.getZ(); z++) {
                    BlockPos pos = new BlockPos(min.getX() + x, min.getY() + y, min.getZ() + z);

                    IBlockState state = world.getBlockState(pos);
                    String id = getBlockStateStringFromState(state);
                    if (id.contains(":")) mods.add(id.split(":")[0]);
                    states.put(state, new BlockPos(x, y, z));

                    TileEntity tileEntity = world.getChunkFromBlockCoords(pos).getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);
                    if (tileEntity != null) {
                        NBTTagCompound tileEntityNBT = tileEntity.serializeNBT();
                        tileEntityNBT.setInteger("x", tileEntityNBT.getInteger("x") - min.getX());
                        tileEntityNBT.setInteger("y", tileEntityNBT.getInteger("y") - min.getY());
                        tileEntityNBT.setInteger("z", tileEntityNBT.getInteger("z") - min.getZ());

                        schematic.tileEntities.add(tileEntityNBT);
                    }
                }
            }
        }

        IBlockState[] keys = states.keySet().toArray(new IBlockState[states.keySet().size()]);

        for (short i = 0; i < keys.length; i++) {
            for (BlockPos pos : states.get(keys[i])) {
                schematic.blockData[pos.getX()][pos.getY()][pos.getZ()] = i;
            }

            schematic.palette.add(i, keys[i]);
        }

        for (Entity entity : world.getEntitiesInAABBexcluding(null, getBoundingBox(from, to), entity -> !(entity instanceof EntityPlayerMP))) {
            NBTTagCompound entityNBT = entity.serializeNBT();

            NBTTagList posNBT = (NBTTagList) entityNBT.getTag("Pos");
            NBTTagList newPosNBT = new NBTTagList();
            newPosNBT.appendTag(new NBTTagDouble(posNBT.getDoubleAt(0) - from.getX()));
            newPosNBT.appendTag(new NBTTagDouble(posNBT.getDoubleAt(1) - from.getY()));
            newPosNBT.appendTag(new NBTTagDouble(posNBT.getDoubleAt(2) - from.getZ()));
            entityNBT.setTag("Pos", newPosNBT);

            schematic.entities.add(entityNBT);
        }

        schematic.requiredMods = mods.toArray(new String[mods.size()]);
        schematic.paletteMax = keys.length - 1;
        schematic.creationDate = System.currentTimeMillis();

        return schematic;
    }

    private static AxisAlignedBB getBoundingBox(Vector3i pos1, Vector3i pos2) {
        return new AxisAlignedBB(new BlockPos(pos1.getX(), pos1.getY(), pos1.getZ()), new BlockPos(pos2.getX(), pos2.getY(), pos2.getZ()));
    }

    public static void place(Schematic schematic, World world, int xBase, int yBase, int zBase) { // TODO: check if entities and tileentities are within pocket bounds
        // Place the schematic's blocks
        setBlocks(schematic, world, xBase, yBase, zBase);

        // Set TileEntity data
        for (NBTTagCompound tileEntityNBT : schematic.tileEntities) {
            BlockPos pos = new BlockPos(
                    xBase + tileEntityNBT.getInteger("x"),
                    yBase + tileEntityNBT.getInteger("y"),
                    zBase + tileEntityNBT.getInteger("z"));
            TileEntity tileEntity = world.getTileEntity(pos);
            if (tileEntity != null) {
                String schematicTileEntityId = tileEntityNBT.getString("id");
                String blockTileEntityId = TileEntity.getKey(tileEntity.getClass()).toString();
                if (schematicTileEntityId.equals(blockTileEntityId)) {
                    tileEntity.readFromNBT(tileEntityNBT);

                    // Correct the position
                    tileEntity.setWorld(world);
                    tileEntity.setPos(pos);
                    tileEntity.markDirty();
                } else {
                    throw new RuntimeException("Schematic contained TileEntity " + schematicTileEntityId + " at " + pos + " but the TileEntity of that block (" + world.getBlockState(pos) + ") must be " + blockTileEntityId);
                }
            } else {
                throw new RuntimeException("Schematic contained TileEntity info at " + pos + " but the block there (" + world.getBlockState(pos) + ") has no TileEntity.");
            }
        }

        // Spawn entities
        for (NBTTagCompound entityNBT : schematic.entities) {
            // Correct the position and UUID
            NBTTagList posNBT = (NBTTagList) entityNBT.getTag("Pos");
            NBTTagList newPosNBT = new NBTTagList();
            newPosNBT.appendTag(new NBTTagDouble(posNBT.getDoubleAt(0) + xBase));
            newPosNBT.appendTag(new NBTTagDouble(posNBT.getDoubleAt(1) + yBase));
            newPosNBT.appendTag(new NBTTagDouble(posNBT.getDoubleAt(2) + zBase));
            NBTTagCompound adjustedEntityNBT = entityNBT.copy();
            adjustedEntityNBT.setTag("Pos", newPosNBT);
            adjustedEntityNBT.setUniqueId("UUID", UUID.randomUUID());

            Entity entity = EntityList.createEntityFromNBT(adjustedEntityNBT, world);
            world.spawnEntity(entity);
        }
    }

    public void setBlockState(int x, int y, int z, IBlockState state) {
        if (palette.contains(state)) {
            blockData[x][y][z] = (short) palette.indexOf(state); // TODO: optimize this (there must be some efficient list implementations)
        } else {
            palette.add(state);
            blockData[x][y][z] = (short) ++paletteMax;
        }
    }

    private static void setBlocks(Schematic schematic, World world, int xBase, int yBase, int zBase) {
        long setTime = 0;
        long relightTime = 0;
        // CubicChunks makes cubic worlds implement ICubicWorld
        // Just "world instanceof ICubicWorld" would throw a class not found error
        //noinspection InstanceofIncompatibleInterface
        if (cubicChunks && world instanceof ICubicWorld) {
            DimDoors.log.info("Setting cube blockstates");
            ICubicWorld cubicWorld = (ICubicWorld) world;
            for (int cubeX = 0; cubeX <= (schematic.width >> 4) + 1; cubeX++) {
                for (int cubeY = 0; cubeY <= (schematic.length >> 4) + 1; cubeY++) {
                    for (int cubeZ = 0; cubeZ <= (schematic.height >> 4) + 1; cubeZ++) {
                        long setStart = System.nanoTime();
                        // Get the cube only once for efficiency
                        Cube cube = cubicWorld.getCubeFromCubeCoords((xBase << 4) + cubeX, (yBase << 4) + cubeY, (zBase << 4) + cubeZ);
                        ExtendedBlockStorage storage = cube.getStorage();
                        boolean setAir = storage != null;
                        for (int x = 0; x < 16; x++) {
                            for (int y = 0; y < 16; y++) {
                                for (int z = 0; z < 16; z++) {
                                    int sx = (cubeX << 4) + x - (xBase & 0x0F);
                                    int sy = (cubeY << 4) + y - (yBase & 0x0F);
                                    int sz = (cubeZ << 4) + z - (zBase & 0x0F);
                                    if (sx >= 0 && sy >= 0 && sz >= 0 && sx < schematic.width && sy < schematic.height && sz < schematic.length) {
                                        IBlockState state = schematic.palette.get(schematic.blockData[sx][sy][sz]);
                                        if (!state.getBlock().equals(Blocks.AIR)) {
                                            if (storage == null) {
                                                cube.setStorage(storage = new ExtendedBlockStorage(cube.getY() << 4, world.provider.hasSkyLight()));
                                            }
                                            storage.set(x, y, z, state);
                                        } else if (setAir) {
                                            storage.set(x, y, z, state);
                                        }
                                    }
                                }
                            }
                        }
                        setTime += System.nanoTime() - setStart;
                        long relightStart = System.nanoTime();
                        // TODO: It's possible to relight a whole region at once, and immediately using this, according
                        // TODO: to Foghrye4: https://hastebin.com/hociqufabe.java
                        cube.setInitialLightingDone(false);
                        relightTime += System.nanoTime() - relightStart;
                        cube.markDirty();
                    }
                }
            }
        } else {
            DimDoors.log.info("Setting chunk blockstates");
            for (int chunkX = 0; chunkX <= (schematic.width >> 4) + 1; chunkX++) {
                for (int chunkZ = 0; chunkZ <= (schematic.length >> 4) + 1; chunkZ++) {
                    long setStart = System.nanoTime();
                    // Get the chunk only once for efficiency
                    Chunk chunk = world.getChunkFromChunkCoords((xBase >> 4) + chunkX, (zBase >> 4) + chunkZ);
                    ExtendedBlockStorage[] storageArray = chunk.getBlockStorageArray();
                    for (int storageY = 0; storageY <= (schematic.height >> 4) + 1; storageY++) {
                        // Get the storage only once for eficiency
                        ExtendedBlockStorage storage = storageArray[(yBase >> 4) + storageY];
                        boolean setAir = storage != null;
                        for (int x = 0; x < 16; x++) {
                            for (int y = 0; y < 16; y++) {
                                for (int z = 0; z < 16; z++) {
                                    int sx = (chunkX << 4) + x - (xBase & 0x0F);
                                    int sy = (storageY << 4) + y - (yBase & 0x0F);
                                    int sz = (chunkZ << 4) + z - (zBase & 0x0F);
                                    if (sx >= 0 && sy >= 0 && sz >= 0 && sx < schematic.width && sy < schematic.height && sz < schematic.length) {
                                        IBlockState state = schematic.palette.get(schematic.blockData[sx][sy][sz]);
                                        if (!state.getBlock().equals(Blocks.AIR)) {
                                            if (storage == null) {
                                                storageArray[storageY] = storage = new ExtendedBlockStorage(storageY << 4, world.provider.hasSkyLight());
                                            }
                                            storage.set(x, y, z, state);
                                        } else if (setAir) {
                                            storage.set(x, y, z, state);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    setTime += System.nanoTime() - setStart;
                    long relightStart = System.nanoTime();
                    chunk.setLightPopulated(false);
                    chunk.setTerrainPopulated(true);
                    chunk.resetRelightChecks();
                    chunk.checkLight();
                    relightTime += System.nanoTime() - relightStart;
                    chunk.markDirty();
                }
            }
        }
        world.markBlockRangeForRenderUpdate(xBase, yBase, zBase, xBase + schematic.width, yBase + schematic.height, zBase + schematic.length);
        DimDoors.log.info("Set block states in " + setTime / 1000000 + " ms and relit chunks/cubes in " + relightTime / 1000000);
    }
}
