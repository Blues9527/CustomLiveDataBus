package com.example.blues.customlivedatabus;

import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.support.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class LiveDataBus {

    private final Map<String, MutableLiveData<Object>> bus;


    private LiveDataBus() {
        bus = new HashMap<>();
    }

    private static class singletonHolder {
        private static final LiveDataBus DEFAULT_BUS = new LiveDataBus();
    }

    public static LiveDataBus get() {
        return singletonHolder.DEFAULT_BUS;
    }

    public synchronized <T> MutableLiveData<T> with(String key, Class<T> type) {
        if (!bus.containsKey(key)) {
            bus.put(key, new BusMutableLiveData<Object>());
        }
        return (MutableLiveData<T>) bus.get(key);
    }

    //通过hook去实现事件拦截
    public class BusMutableLiveData<T> extends MutableLiveData<T> {
        @Override
        public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<T> observer) {
            super.observe(owner, observer);


            try {
                hook(observer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void hook(Observer<T> observer) throws Exception {

            /**
             * 思路是：要在执行observer的onChanged方法前进行hook
             * onChanged方法前有个判断版本号的，即比较mVersion 和mLastVersion
             *
             * 如果和mLastVersion >= mVersion，就执行return而不会去执行哦nChanged、方法从而达到hook效果
             */

            //通过反射去拿到LiveData类对象
            Class<LiveData> liveDataClass = LiveData.class;


            //拿到mObservers 变量,是一个map对象
            Field mObservers = liveDataClass.getDeclaredField("mObservers");

            //private变量，要设置为可操作
            mObservers.setAccessible(true);

            Object objectObservers = mObservers.get(this);

            Class<?> classObservers = objectObservers.getClass();

            Method methodGet = classObservers.getDeclaredMethod("get", Object.class);

            methodGet.setAccessible(true);

            Object objectWrapperEntry = methodGet.invoke(objectObservers, observer);

            Object objectWrapper = null;

            if (objectWrapperEntry instanceof Map.Entry) {
                objectWrapper = ((Map.Entry) objectWrapperEntry).getValue();
            }

            if (objectWrapper == null) {
                throw new NullPointerException("wrapper is null");
            }

            Class<?> classObjectWrapper = objectWrapper.getClass().getSuperclass();


            Field mLastVersion = classObjectWrapper.getDeclaredField("mLastVersion");

            mLastVersion.setAccessible(true);

            //拿到mVersion变量
            Field mVersion = liveDataClass.getDeclaredField("mVersion");

            //是private的所以要设置为可操作
            mVersion.setAccessible(true);

            Object objectVersion = mVersion.get(this);


            mLastVersion.set(objectWrapper, objectVersion);


        }
    }

}
