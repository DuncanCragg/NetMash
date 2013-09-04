
if(!entity.worldObj.isRemote){
    EntityCow cow=new EntityCow(entity.worldObj);
    cow.setLocationAndAngles(entity.posX, entity.posY, entity.posZ, entity.rotationYaw, entity.rotationPitch);
    entity.worldObj.spawnEntityInWorld(cow);
}


if(style.equals("placing")){
    String name="Duncster";
    MinecraftServer server=MinecraftServer.getServer();
    ServerConfigurationManager scm=server.getConfigurationManager();
    EntityPlayerMP person = scm.createPlayerForUser(name);
    if(person == null){ System.out.println("Can't create player in createPlayerForUser "+name); return; }
    WorldServer worldserver = server.worldServerForDimension(person.dimension);
    person.setWorld(worldserver);
    person.theItemInWorldManager.setWorld((WorldServer)person.worldObj);
    person.setLocationAndAngles(x, y, z, entity.rotationYaw, entity.rotationPitch);
    worldserver.spawnEntityInWorld(person);
}

 // { is: cow position: .. } .. who drives Cyrus params now? alive, position, speed/motion

    EntityCow cow=new EntityCow(entity.worldObj);
    cow.setLocationAndAngles(entity.posX, entity.posY, entity.posZ, entity.rotationYaw, entity.rotationPitch);
    entity.worldObj.spawnEntityInWorld(cow);


-----------------
