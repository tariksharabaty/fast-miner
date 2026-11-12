package com.oidioomstudio.fastminer.client;

import com.oidioomstudio.fastminer.FastMinerMod;
import com.oidioomstudio.olib.client.OModMenuApi;

public class ModMenuIntegration extends OModMenuApi {
    @Override
    protected String getModId() {
        return FastMinerMod.MOD_ID;
    }
}
