package com.aw.parrot;

import com.unisound.sim.http.adapter.NluAdapter;
import com.unisound.sim.im.IMListener;
import com.unisound.sim.im.VoiceIM;
import com.unisound.sim.im.nlu.ICustomNLU;

public class VoiceIMHelper {
    public static String APP_KEY = "4krtkzdrip5fzcm3h5nyad7biv6hapdf7ctxwjyu";
    public static String APP_SECRET = "250576fe473eef05042b47b149e64cbd";
    
    private static VoiceIMHelper mInstance;
    
    private VoiceIM mVoiceIM;
    
    private VoiceIMHelper() {
        mVoiceIM = new VoiceIM(APP_KEY, APP_SECRET, MainApplication.getInstance());
        mVoiceIM.setOption(VoiceIM.OPT_ACTIVATE_NLU_RESULT, true);
    }

    public synchronized static VoiceIMHelper getInstance() {
        if (mInstance == null) {
            mInstance = new VoiceIMHelper();
        }
        return mInstance;
    }
    
    public void start() {
        mVoiceIM.start();
    }
    
    public VoiceIM getVoiceIM() {
        return mVoiceIM;
    }
    
    public void setIMListener(IMListener lis) {
        mVoiceIM.setIMListener(lis);
    }
    
    public void setCustomNLU(ICustomNLU nlu) {
        mVoiceIM.setCustomNLU(nlu);
    }
    
    public void onDestory() {
        mVoiceIM.onDestory();
        mVoiceIM = null;
        mInstance = null;
    }
    
}
