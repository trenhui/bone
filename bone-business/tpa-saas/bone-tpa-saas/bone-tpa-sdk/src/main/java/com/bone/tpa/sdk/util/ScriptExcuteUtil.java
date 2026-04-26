package com.bone.tpa.sdk.util;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;

public class ScriptExcuteUtil {
    private static ScriptEngineManager engineManager = new ScriptEngineManager();
    private static ScriptEngine engine = engineManager.getEngineByName("javascript");

    private static final Object lock = new Object();

    static public Object runScirpt(String script, String method, Object... args) throws ScriptException,
            NoSuchMethodException {
        synchronized (lock) {
            engine.eval(script);
            Object result = ((Invocable) engine).invokeFunction(method, args);
            return result;
        }


    }
}
