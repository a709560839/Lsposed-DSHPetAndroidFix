package com.jj.dshpet.lsposed.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 反射操作工具类，提供安全便捷的字段读写、方法调用及类成员查找功能（支持父类链递归查找）。
 */
public class ReflectionUtils {

    /**
     * 设置对象中指定名称的 int 类型的字段值
     */
    public static void setIntField(Object obj, String name, int val) {
        if (obj == null) return;
        try {
            Field f = findField(obj.getClass(), name);
            if (f == null) return;
            f.setAccessible(true);
            f.setInt(obj, val);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 设置对象中指定名称的 boolean 类型的字段值
     */
    public static void setBooleanField(Object obj, String name, boolean val) {
        if (obj == null) return;
        try {
            Field f = findField(obj.getClass(), name);
            if (f == null) return;
            f.setAccessible(true);
            f.setBoolean(obj, val);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 获取对象中指定名称的字段值
     */
    public static Object getField(Object obj, String name) {
        if (obj == null) return null;
        try {
            Field f = findField(obj.getClass(), name);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 获取对象中指定名称的 boolean 类型字段值
     */
    public static boolean getBooleanField(Object obj, String name) {
        if (obj == null) return false;
        try {
            Field f = findField(obj.getClass(), name);
            if (f == null) return false;
            f.setAccessible(true);
            return f.getBoolean(obj);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 获取对象中指定名称的 int 类型字段值
     */
    public static int getIntField(Object obj, String name) {
        if (obj == null) return 0;
        try {
            Field f = findField(obj.getClass(), name);
            if (f == null) return 0;
            f.setAccessible(true);
            return f.getInt(obj);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * 获取对象中指定名称的 float 类型字段值
     */
    public static float getFloatField(Object obj, String name) {
        if (obj == null) return 0f;
        try {
            Field f = findField(obj.getClass(), name);
            if (f == null) return 0f;
            f.setAccessible(true);
            return f.getFloat(obj);
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    /**
     * 在类及其父类链中递归查找指定名称的字段
     */
    public static Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    /**
     * 调用实例对象的无参方法
     */
    public static void invokeNoArg(Object obj, String methodName) {
        if (obj == null) return;
        try {
            Method m = findMethod(obj.getClass(), methodName, new Class[0]);
            if (m == null) return;
            m.setAccessible(true);
            m.invoke(obj);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 调用实例对象的指定参数方法
     */
    public static void invokeMethod(Object obj, String methodName, Class<?>[] paramTypes, Object[] args) {
        if (obj == null) return;
        try {
            Method m = findMethod(obj.getClass(), methodName, paramTypes);
            if (m == null) return;
            m.setAccessible(true);
            m.invoke(obj, args);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 调用静态无参方法
     */
    public static Object invokeStaticNoArg(Class<?> clazz, String methodName) {
        try {
            Method m = findMethod(clazz, methodName, new Class[0]);
            if (m == null) return null;
            m.setAccessible(true);
            return m.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 调用带参数的静态方法
     */
    public static Object invokeStaticMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes, Object[] args) {
        try {
            Method m = findMethod(clazz, methodName, paramTypes);
            if (m == null) return null;
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 在类及其父类链中递归查找指定名称和参数类型的重载方法
     */
    public static Method findMethod(Class<?> clazz, String name, Class<?>[] paramTypes) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
