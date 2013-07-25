
if(!entity.worldObj.isRemote){
    EntityCow cow=new EntityCow(entity.worldObj);
    cow.setLocationAndAngles(entity.posX, entity.posY, entity.posZ, entity.rotationYaw, entity.rotationPitch);
    entity.worldObj.spawnEntityInWorld(cow);
}

