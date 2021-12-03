package com.example.tdycamera.mycamera.camerax;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

/**
 * 自定义camerax的生命周期
 */
public class CustomLifecycle implements LifecycleOwner {
    private LifecycleRegistry lifecycleRegistry;
    public CustomLifecycle() {
        lifecycleRegistry = new LifecycleRegistry(this);
        lifecycleRegistry.markState(Lifecycle.State.CREATED);
    }

    public void doOnResume() {
        lifecycleRegistry.markState(Lifecycle.State.RESUMED);
    }

    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }
}