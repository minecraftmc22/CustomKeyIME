package com.minecraftmc22.ckime;

/**
 * 一个按键分组（含分组名 + 分组内的所有按键）。
 * 分组顺序和按键顺序都按列表里出现的顺序排列。
 */
public class KeyGroup {
    public String name;
    public final java.util.List<CustomKey> keys = new java.util.ArrayList<>();

    public KeyGroup(String name) {
        this.name = name;
    }
}