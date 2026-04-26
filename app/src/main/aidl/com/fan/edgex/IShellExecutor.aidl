package com.fan.edgex;

import com.fan.edgex.IShellCallback;

oneway interface IShellExecutor {
    void execute(String command, boolean runAsRoot, IShellCallback callback);
}
